# okhttp3源码解析(9)-Okio
## 前言
上两篇文章写了下okhttp3的缓存功能，里面用到了很多okio的内容，在网络请求的部分也用到了很多，我觉得还是有必要研究下的。一开始看okhttp3源码的时候，我也不知道怎么入手，后面发现不如直接就从最简单的使用开始，看到什么研究什么。下面的okio我也打算这么做。

其实我再写文章前找了些资料看了下，已经有作者写了很好的文章了，读者可以先看下别人的，图文并茂。

[Android IO 框架 Okio 的实现原理，到底哪里 OK？](https://juejin.cn/post/7167757174502850597)
[Android IO 框架 Okio 的实现原理，如何检测超时？](https://juejin.cn/post/7168097359807971342)
[值得一用的IO神器Okio](https://juejin.cn/post/6923902848908394510)

我这就只能说是自己看源码的理解了，比较生硬，比较无聊，仅供参考。

## Okio使用案例
和okhttp3一开始研究一样，我们从Okio的用法开始，我从okhttp的源码里面找了一些例子，可以先看下:
```
  // DiskLruCache
  synchronized void rebuildJournal() throws IOException {
    // ...
    BufferedSink writer = Okio.buffer(fileSystem.sink(journalFileTmp));
    try {
      writer.writeUtf8(MAGIC).writeByte('\n');
      writer.writeUtf8(VERSION_1).writeByte('\n');
      writer.writeDecimalLong(appVersion).writeByte('\n');
      writer.writeDecimalLong(valueCount).writeByte('\n');
      writer.writeByte('\n');

      for (Entry entry : lruEntries.values()) {
        if (entry.currentEditor != null) {
          writer.writeUtf8(DIRTY).writeByte(' ');
          writer.writeUtf8(entry.key);
          writer.writeByte('\n');
        } else {
          writer.writeUtf8(CLEAN).writeByte(' ');
          writer.writeUtf8(entry.key);
          entry.writeLengths(writer);
          writer.writeByte('\n');
        }
      }
    } finally {
      writer.close();
    }
    // ...
  }
```
```
  // Http1Codec
  private class UnknownLengthSource extends AbstractSource {
    private boolean inputExhausted;
    // ...
    @Override public long read(Buffer sink, long byteCount)
        throws IOException {
      if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
      if (closed) throw new IllegalStateException("closed");
      if (inputExhausted) return -1;

      long read = super.read(sink, byteCount);
      if (read == -1) {
        inputExhausted = true;
        endOfInput(true, null);
        return -1;
      }
      return read;
    }
    // ...
  }
```
找了两个例子，一个读一个写，分别来自DiskLruCache和Http1Codec，一个是对IO文件的处理，一个是对socket流的处理，okio主要也是用在这两部分。这里的例子看起来还不是很明显，我们再来看下okio简单的使用: 
```
    // OKio写文件 
    private static void writeFileByOKio() {
        try (Sink sink = Okio.sink(new File(path));
             BufferedSink bufferedSink = Okio.buffer(sink)) {
            bufferedSink.writeUtf8("write" + "\n" + "success!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    //OKio读文件
    private static void readFileByOKio() {
        try (Source source = Okio.source(new File(path));
             BufferedSource bufferedSource = Okio.buffer(source)) {
            for (String line; (line = bufferedSource.readUtf8Line()) != null; ) {
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
```
对于Java的IO来说，这里看起来还是比较简洁的，Java要读取个文件要一层一层的封装Stream，类似下面代码:
```
fileStream = new FileInputStream(path);
binStream = new BufferedInputStream(fileStream);
dataInputStream = new DataInputStream(binStream);
dataInputStream.readInt();
```
实际上就是BufferedSource和BufferedSink两个，把大部分活都干了，而Java的要将BufferedInputStream/BufferedOutputStream转成专门处理的类去干活，各有各的好处吧，但是对于我们搬砖的来说okio肯定用起来简单，这点就够了。

## Okio类分析
从上面例子可以看出，Okio类就是okio的一个入口，用来提供source和sink，可以看下它的源码，全是static的方法，大致归类下就三种:
1. 生成sink
2. 生成source
3. 生成buffer

生成sink和source有几种方式: file、path、socket、inputStream\outputStream，其实最后都是通过stream的形式创建的:
```
private static Sink sink(final OutputStream out, final Timeout timeout) {
    // ...
    return new Sink() {
      @Override public void write(Buffer source, long byteCount) throws IOException {
        // 检查大小参数是否正确
        checkOffsetAndCount(source.size, 0, byteCount);
        while (byteCount > 0) {
          // 同步超时检测
          timeout.throwIfReached();
          // segment是缓存的最小单位，使用双向链表保存，可以共享
          Segment head = source.head;
          // head.limit按注释解释，是当前segment下一个可读取字节的位置->所以相减得到长度
          int toCopy = (int) Math.min(byteCount, head.limit - head.pos);
          out.write(head.data, head.pos, toCopy);

          // 这个head都会pop了还有必要加上读取字节数吗？也可能没有pop啊!得加上
          head.pos += toCopy;
          byteCount -= toCopy;
          source.size -= toCopy;

          // 意思就是head这个segment读取完了吧
          if (head.pos == head.limit) {
            source.head = head.pop();
            // SegmentPool会暂时储存读取完的Segment，当然呗共享出去的不会进入SegmentPool
            SegmentPool.recycle(head);
          }
        }
      }
      // 暂时忽略其他方法
    };
  }
```
```
private static Source source(final InputStream in, final Timeout timeout) {
    // ...
    return new Source() {
      @Override public long read(Buffer sink, long byteCount) throws IOException {
        if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        if (byteCount == 0) return 0;
        try {
          timeout.throwIfReached();
          // 没有则从SegmentPool拿，又就通过双向链表移到最后(head的pre就是最后)
          Segment tail = sink.writableSegment(1);
          // Segment缓存种剩余能写入的最大空间，tail.limit下一个能写入的位置
          int maxToCopy = (int) Math.min(byteCount, Segment.SIZE - tail.limit);
          // 把数据读取到tail里面，tail.limit是offset
          int bytesRead = in.read(tail.data, tail.limit, maxToCopy);
          if (bytesRead == -1) return -1;
          
          // 这个是Segment下一个能写入的位置
          tail.limit += bytesRead;
          // 这个是sink的总大小
          sink.size += bytesRead;
          return bytesRead;
        } catch (AssertionError e) {
          if (isAndroidGetsocknameError(e)) throw new IOException(e);
          throw e;
        }
      }
      // 暂时忽略其他方法
    };
  }
```
可以看到sink和source方法都是自己创建了一个实例进行返回，主要重写了其中的read和write方法，这两个方法里面出现了很多很多东西，这些出现的东西就是我们接下来要研究的，主要有下面几个内容:
- Buffer，缓存
- timeout，超时检测
- Segment，分段缓存
- SegmentPool，缓存池

注释解释了一些，但是还是得系统的研究下，下面我们开始研究。

## Okio继承结构
在解析Okio的具体内容前，我觉得还是有必要先看下它的继承结构，这里拿别人的图看下，如有侵权请联系啊!

这是Java IO的结构:
![java](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/5280118b032c46e8ac47b4b5ebc0658d~tplv-k3u1fbpfcp-zoom-in-crop-mark:4536:0:0:0.awebp?)

这是Okio的结构:
![okio](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/7d16afb9e4f84c7ba517a3e7788a6bef~tplv-k3u1fbpfcp-zoom-in-crop-mark:4536:0:0:0.awebp?)

这里掉了一个Buffer类的，Buffer继承了BufferedSource和BufferedSink接口，也就是说Buffer既是sink又是source，当然它也有好多自己的功能。

根据上面的图，主要功能就在BufferedSource和BufferedSink里面，它们的具体实现则是RealBufferedSource和RealBufferedSink，关于Segment和SegmentPool的内容上面也没有，下面独立讲解。

下面内容想了挺久，怎么去组织这样一篇博文，怎样更顺畅去看源码，最后我决定由小及大的方式来讲，先从最小的segment开始，到BufferedSource和BufferedSink，最后到buffer，下面看内容。

## Segment
Segment大致就是一个数据类，是双向链表的一个节点，提供了一些操作segment的的方法，下面我们先看数据域:
```
  /** The size of all segments in bytes. */
  static final int SIZE = 8192;

  /** Segments will be shared when doing so avoids {@code arraycopy()} of this many bytes. */
  static final int SHARE_MINIMUM = 1024;

  final byte[] data;

  /** The next byte of application data byte to read in this segment. */
  int pos;

  /** The first byte of available data ready to be written to. */
  int limit;

  /** True if other segments or byte strings use the same byte array. */
  boolean shared;

  /** True if this segment owns the byte array and can append to it, extending {@code limit}. */
  boolean owner;

  /** Next segment in a linked or circularly-linked list. */
  Segment next;

  /** Previous segment in a circularly-linked list. */
  Segment prev;
```
其实注释写的非常清楚了，SIZE是segment的最大容量，SHARE_MINIMUM是分享的数组最短长度(即一个segment能被分成八份)，data表示数据，pos和limit用来标记数据部分区间，shared标记是否被分享，owner表示这个segment是否拥有这个data数组(拥有才可以写、追加，被分享的不能写)，pre和next是双向指针。

数据域看完了，大致就是一个数据类，下面看下功能。

### 分享
Segment提供了分享的操作，有两种，一种是共享data数组的分享，一种是深度拷贝，分享出去的对象完全是一个克隆体:
```
  final Segment sharedCopy() {
    shared = true;
    return new Segment(data, pos, limit, true, false);
  }
  
  final Segment unsharedCopy() {
    return new Segment(data.clone(), pos, limit, false, true);
  }
```
看到这里，应该就能理解shared和owner两个标志了吧，第一个函数总data是数组，不会进行赋值，而是会被新的segment持有引用，深度拷贝分享的shared为false、owner为true，就表示它是一个全新的segment只是数据内容和复制的一样。

### segment操作方法
剩下的方法就是对segment的操作了，有点类似stack，这里实现了pop和push方法来对链表操作，注意下这些操作是头插法。

spilt方法很有意思，我们这里看下，也有利于理解pop和push:
```
public final Segment split(int byteCount) {
    if (byteCount <= 0 || byteCount > limit - pos) throw new IllegalArgumentException();
    Segment prefix;

    // 分了两种情况，一个是直接分享了，一个是创建个新的segment
    if (byteCount >= SHARE_MINIMUM) {
      prefix = sharedCopy();
    } else {
      prefix = SegmentPool.take();
      // 将pos开始的前byteCount个数据复制到新segment
      System.arraycopy(data, pos, prefix.data, 0, byteCount);
    }
    
    // 确定新segment地尾端
    prefix.limit = prefix.pos + byteCount;
    // 把当前segment跳过被split的byteCount个字节，即按pos读的时候跳过一部分
    pos += byteCount;
    // 头插法，新的segment放入链表头部
    prev.push(prefix);
    return prefix;
  }
```

## SegmentPool
都说到Segment了，SegmentPool当然得提下了，这个类很短，就六十多行代码，它唯一的目的就是把用不到的segment利用起来，防止重复创建对象带来的损耗，类是线程安全的。
> A collection of unused segments, necessary to avoid GC churn and zero-fill. This pool is a thread-safe static singleton.

SegmentPool就两个方法: take和recycle，三个变量，MAX_SIZE定义了SegmentPool的最大容量(64 * 1024 = 8 * 8192，即默认八个segment)，next变量指向了下一个可以使用的segment，byteCount变量记录了这个SegmentPool实际使用的字节数。

### take方法
我们先来看下take方法，用来取的一个segment以供使用:
```
  static Segment take() {
    synchronized (SegmentPool.class) {
      if (next != null) {
        Segment result = next;
        next = result.next;
        result.next = null;
        byteCount -= Segment.SIZE;
        return result;
      }
    }
    return new Segment(); // Pool is empty. Don't zero-fill while holding a lock.
  }
```
这里就是链表操作，把next取出来，next的next作为新的next保存到SegmentPool中。如果next为空，就创建一个新的Segment，这里写在同步代码外，还加了句注释，我看得不是很懂，意思是在next为空的时候，recycle的同步代码不要往里面push值吗？

### recycle方法
```
  static void recycle(Segment segment) {
    // 不能乱了SegmentPool的链表
    if (segment.next != null || segment.prev != null) throw new IllegalArgumentException();
    // segment被分享，其中的data还在使用，不能复用，也不会被GC回收，所以不用管
    if (segment.shared) return; // This segment cannot be recycled.
    synchronized (SegmentPool.class) {
      if (byteCount + Segment.SIZE > MAX_SIZE) return; // Pool is full.
      // 这里按segment最大数量算，所以SegmentPool中的segment个数是整数
      byteCount += Segment.SIZE;
      segment.next = next;
      segment.pos = segment.limit = 0;
      next = segment;
    }
  }
```
recycle方法也很短，主要就是做了一些判断，就把新放进来的segment放到了链表头上。

## BufferedSource和BufferedSink
上面提到了Buffer类继承了BufferedSource和BufferedSink，所以在讲Buffer之前，我们先来看下这两个方法。从上面的继承结构我们知道了，BufferedSource和BufferedSink继承自Sink和Source，但其功能由RealBufferedSource和RealBufferedSink实现。下面我们一层一层看。

### Sink和Source接口
Sink和Source就是两个最简单的接口，下面看下源码:
```
public interface Sink extends Closeable, Flushable {
  void write(Buffer source, long byteCount) throws IOException;
  @Override void flush() throws IOException;
  Timeout timeout();
  @Override void close() throws IOException;
}

public interface Source extends Closeable {
  long read(Buffer sink, long byteCount) throws IOException;
  Timeout timeout();
  @Override void close() throws IOException;
}
```
删掉了注释，实际上Sink和Source就是读写加上了timeout的超时控制和Closeable的标记，Sink因为要输出加上了Flushable接口。

### BufferedSource和BufferedSink接口
BufferedSource和BufferedSink都是抽象接口，只是定义了一些方法，两者大体相似，也有不同，下面先来看BufferedSink。

#### BufferedSink接口
BufferedSink方法特别多，我们这归类下，主要有四种:
1. Sink接口的flush方法
2. getter方法: buffer、outputStream
3. 一系列的writeXXX方法，获得BufferedSink
4. 两个emit方法，也返回BufferedSink

flush方法、buffer方法、outputStream方法都比较好理解，BufferedSink接口的主要功能就在这些writeXXX方法上，下面大致收集下write可以写什么:

- ByteString、byte[]、Source、String(Utf8)、codePoint(UTF-8)、
- String(Charset)、Byte、Short(Big-Endian)、ShortLe(Little-Endian低位在低地址端)、
- Int、IntLe、Long、LongLe、DecimalLong(保存为十进制字符串)、HexadecimalUnsignedLong(保存为十六进制字符串)

大致就是基本的类型BufferedSink都可以直接写入，这里就不展开讲了，毕竟功能在RealBufferedSink中实现的，再来看下两个emit方法。实际上这方法的注释写的非常清楚:
```
BufferedSink b0 = new Buffer();
BufferedSink b1 = Okio.buffer(b0);
BufferedSink b2 = Okio.buffer(b1);

b2.writeUtf8("hello");
assertEquals(5, b2.buffer().size());
assertEquals(0, b1.buffer().size());
assertEquals(0, b0.buffer().size());

b2.emit();
assertEquals(0, b2.buffer().size());
assertEquals(5, b1.buffer().size());
assertEquals(0, b0.buffer().size());

b1.emit();
assertEquals(0, b2.buffer().size());
assertEquals(0, b1.buffer().size());
assertEquals(5, b0.buffer().size());
```
这里说什么都不如看例子来的多，对于emitCompleteSegments也有注释，但是好像更难理解一些:
```
/**
 * Writes complete segments to the underlying sink, if one exists. Like {@link #flush}, but
 * weaker. Use this to limit the memory held in the buffer to a single segment. Typically
 * application code will not need to call this: it is only necessary when application code writes
 * directly to this {@linkplain #buffer() sink's buffer}. <pre>{@code
 */
BufferedSink b0 = new Buffer();
BufferedSink b1 = Okio.buffer(b0);
BufferedSink b2 = Okio.buffer(b1);

b2.buffer().write(new byte[20_000]);
assertEquals(20_000, b2.buffer().size());
assertEquals(     0, b1.buffer().size());
assertEquals(     0, b0.buffer().size());

b2.emitCompleteSegments();
assertEquals( 3_616, b2.buffer().size());
assertEquals(     0, b1.buffer().size());
assertEquals(16_384, b0.buffer().size()); // This example assumes 8192 byte segments.
```
结合英语理解下，大致意思就是这个emit是完全提交，无论封装多少层，最后都是提交到原始的Buffer上，但是这个提交是看segment的，只有完整的segment才会被emit，及达到SIZE(8192)的，没达到的不提交。而且这个基本是RealBufferedSink中的writeXXX就处理了，用户除非操作了原始的buffer，不然是用不着的。

#### BufferedSource接口
BufferedSource接口和BufferedSink接口类似，没了flush方法，也多了几种方法:
1. 判断读取完的exhausted方法
2. getter方法: buffer、inputStream
3. 一系列的readXXX方法，获得数据
4. 用来跳过的skip方法
5. 用来判断要读取数据是否超出长度的require和request方法
6. 用来判断一行前缀的select方法
7. 一系列的indexOf方法
8. 用来比较范围内相等的rangeEquals方法

exhausted方法就是source内没数据了，require和request方法都是判断取的数据是否超长，request会报异常，request则是返回false，readXXX方法和上面的writeXXX类似，skip方法就是跳过，没什么好讲的，这里就讲下后面三种方法。

对于select方法，也是看注释:
```
/**
 * Finds the first string in {@code options} that is a prefix of this buffer, consumes it from
 * this buffer, and returns its index. If no byte string in {@code options} is a prefix of this
 * buffer this returns -1 and no bytes are consumed.
 */
Options FIELDS = Options.of(
    ByteString.encodeUtf8("depth="),
    ByteString.encodeUtf8("height="),
    ByteString.encodeUtf8("width="));

Buffer buffer = new Buffer()
    .writeUtf8("width=640\n")
    .writeUtf8("height=480\n");

assertEquals(2, buffer.select(FIELDS));
assertEquals(640, buffer.readDecimalLong());
assertEquals('\n', buffer.readByte());
assertEquals(1, buffer.select(FIELDS));
assertEquals(480, buffer.readDecimalLong());
assertEquals('\n', buffer.readByte());
```
这里最好看下英文注释，一开始我以为是用来筛选的，后面发现理解错了，这个是对prefix的一种识别，返回定义在options中的index，options里面没有就返回-1。

indexOf方法也没什么好说的，就是对byte、ByteString、Element(多个匹配字符任选)的匹配，支持fromIndex和toIndex之类的。

rangeEquals方法和indexOf类似，都是在范围内判断是否相等，不过rangeEquals方法返回的是true or false。

### RealBufferedSource和RealBufferedSink
虽然说RealBufferedSource和RealBufferedSink最终实现了BufferedSource和BufferedSink接口，但是打开源码，我们却可以发现里面的操作基本都是交给了Buffer去处理的，Buffer我们下一节看，这里我们就来看下这两个类加了Real之后有什么不同。

#### RealBufferedSource
这里先来看下数据域和构造函数:
```
  public final Buffer buffer = new Buffer();
  public final Source source;
  boolean closed;

  RealBufferedSource(Source source) {
    if (source == null) throw new NullPointerException("source == null");
    this.source = source;
  }
```
RealBufferedSource内保存了外部传来的source，buffer是直接new出来的，closed变量是通过调用close方法控制的，当其被标记为true的时候就不能使用了:
```
  @Override public void close() throws IOException {
    if (closed) return;
    closed = true;
    source.close();
    buffer.clear();
  }
  
  @Override public boolean isOpen() {
    return !closed;
  }
```
这里的close方法是从Channel接口过来的，Channel还有个方法isOpen，其实Channel这个接口是从BufferedSource来的:
> RealBufferedSource -> BufferedSource -> ReadableByteChannel -> Channel

这里有个ReadableByteChannel方法，前面讲BufferedSource时，BufferedSource并没有实现它(三个方法)，所以跳过了，现在我们继续看下ReadableByteChannel接口:
```
public interface ReadableByteChannel extends Channel {
    public int read(ByteBuffer dst) throws IOException;
}
```
比BufferedSource众多readXXX多了一个read方法，而且这个是从ByteBuffer读取的，这个ByteBuffer很长，我们只要知道它是一个Buffer就行了。
```
public abstract class ByteBuffer extends Buffer implements Comparable<ByteBuffer> { ... }
```
看完从ReadableByteChannel和Channel来的方法，RealBufferedSource就剩下来自Source和BufferedSource接口的方法了，并没有其自己的额外方法。

下面我们挑个方法讲下，主要还是得看Buffer的内容:
```
  @Override public long read(Buffer sink, long byteCount) throws IOException {
    if (sink == null) throw new IllegalArgumentException("sink == null");
    if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
    if (closed) throw new IllegalStateException("closed");

    // buffer没数据，向buffet里面读取一个segment的长度
    if (buffer.size == 0) {
      long read = source.read(buffer, Segment.SIZE);
      if (read == -1) return -1;
    }

    // 考虑下byteCount > buffer.size的时候，按buffer.size读，所以read在外部调用的时候需要调用多次(套循环)
    long toRead = Math.min(byteCount, buffer.size);
    // 从buffer读取到sink中去
    return buffer.read(sink, toRead);
  }
```
这个是继承自Source的方法，其他的也大差不差，都是先判断下，然后交给buffer去做，读者可以看下源码，我这就不写了。

#### RealBufferedSink
RealBufferedSink和上面介绍的RealBufferedSource基本一模一样，只是source换成了sink，最终也是通过Buffer去实现的，这里就看下几个不一样的方法。

emitCompleteSegments方法在BufferedSink中提到过，意思是提交写满了数据的segment到最底层的sink，它会在每个writeXXX的最后调用，这里看下它的具体实现:
```
  @Override public BufferedSink writeHexadecimalUnsignedLong(long v) throws IOException {
    if (closed) throw new IllegalStateException("closed");
    buffer.writeHexadecimalUnsignedLong(v);
    // writeXXX的最后都会调用
    return emitCompleteSegments();
  }
  
  @Override public BufferedSink emitCompleteSegments() throws IOException {
    if (closed) throw new IllegalStateException("closed");
    // 得到的是segment最大值的整数倍
    long byteCount = buffer.completeSegmentByteCount();
    // 所以这里写入到sink的是写满的segment
    if (byteCount > 0) sink.write(buffer, byteCount);
    return this;
  }
```

flush方法是RealBufferedSource中没有的，我们这里也看下:
```
  @Override public void flush() throws IOException {
    if (closed) throw new IllegalStateException("closed");
    if (buffer.size > 0) {
      // 全部写入到sink
      sink.write(buffer, buffer.size);
    }
    // 刷新
    sink.flush();
  }
```

close方法也有变化，注意下:
```
@Override public void close() throws IOException {
    if (closed) return;

    // Emit buffered data to the underlying sink. If this fails, we still need
    // to close the sink; otherwise we risk leaking resources.
    Throwable thrown = null;
    try {
      // 先把缓存数据写入再关闭，出错了会丢失数据也没办法
      if (buffer.size > 0) {
        sink.write(buffer, buffer.size);
      }
    } catch (Throwable e) {
      thrown = e;
    }

    try {
      sink.close();
    } catch (Throwable e) {
      if (thrown == null) thrown = e;
    }
    closed = true;

    // 先试试关闭后，再把两个会异常的地方的异常抛出
    if (thrown != null) Util.sneakyRethrow(thrown);
  }
```

## Buffer
讲了那么多终于到Buffer这个最核心的类了！说到buffer，为什么要用buffer，这个就涉及到计算机的操作系统了，访问磁盘和网卡等 IO 操作需要通过系统调用来执行，频繁调用操作系统会带来上下文切换的性能损耗。以读取为例，一次性读取一些数据并保存起来(buffer)，在需要的时候从buffer里读取，这样就能降低性能损耗。 当然，这是我总结的话，可能不太准，大致意思就是这样。

其实在Java里面也用到了buffer，也就是BufferedInputStream和BufferedOutputStream，里面是通过一个byte数组实现的，Okio的buffer则不一样，里面通过Segment双向链表实现buffer，并且buffer还支持共享(上面说到了，可以共享segment中的data数组)，共享可以减少双流复制时不必要的开销。

### Buffer的结构
Buffer有两千多行，一个一个方法去讲有点不现实，和上面一样，我们先看Buffer继承的方法，再来看它自己的方法，并对它自己的方法分类，这样就可以把Buffer看成如下结构:

- Source
- Sink
- Channel
- ReadableByteChannel
- WritableByteChannel
- BufferedSource
- BufferedSink
- 内部类: UnsafeCursor，及四个获取方法
- 加密: md5、sha1等
- snapshot: 获得缓存数据的ByteString
- copy、write、read、getByte(pos)、clear、skip、rangeEquals，对缓存操作
- 获取参数: size、completeSegmentByteCount(segment剩余空间)、segmentSizes
- selectPrefix，判断前缀格式(配合options获得index)
- writableSegment(minimumCapacity)，获取可以够容量的segment

看着有点头大，简单的就不说了，我们下面慢慢说。

### Source、Sink、三个Channel接口
先来看前五个接口的，这几个接口简单，方法数比较少，先来看下Channel接口的实现:
```
  @Override public boolean isOpen() { return true; }
  @Override public void close() {}
```
就这？没错，这两个方法是空的，而ReadableByteChannel和WritableByteChannel就有点东西了:
```
// ReadableByteChannel
@Override public int read(ByteBuffer sink) throws IOException {
    Segment s = head;
    if (s == null) return -1;

    // 最大读入当前segment能剩下的字节数
    int toCopy = Math.min(sink.remaining(), s.limit - s.pos);
    sink.put(s.data, s.pos, toCopy);

    s.pos += toCopy;
    size -= toCopy;     // size是Buffer剩下的size

    // 当前segment读取完，pop并存到SegmentPool去
    if (s.pos == s.limit) {
      head = s.pop();
      SegmentPool.recycle(s);
    }

    return toCopy;
  }
  
// WritableByteChannel
@Override public int write(ByteBuffer source) throws IOException {
    if (source == null) throw new IllegalArgumentException("source == null");

    int byteCount = source.remaining();
    int remaining = byteCount;
    while (remaining > 0) {
      // 从SegmentPool取一个可以写入的segment，至少能写一个字节
      Segment tail = writableSegment(1);

      // 最多可以写入segment剩余的字节数
      int toCopy = Math.min(remaining, Segment.SIZE - tail.limit);
      source.get(tail.data, tail.limit, toCopy);

      remaining -= toCopy;
      tail.limit += toCopy;     // limit就是下一个可以写入的位置
    }

    size += byteCount;
    return byteCount;
  }
```
不是很复杂，主要就在sink.put(..)和source.get(...)方法，这个是ByteBuffer的功能，我们后面再巧，不过也好理解。再来看下Source和Sink的具体实现:
```
  // Source
  @Override public long read(Buffer sink, long byteCount) {
    if (sink == null) throw new IllegalArgumentException("sink == null");
    if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
    if (size == 0) return -1L;
    if (byteCount > size) byteCount = size;
    // 就是从外来的Buffer写入到本Buffer
    sink.write(this, byteCount);
    return byteCount;
  }
  
  // Sink
  @Override public void write(Buffer source, long byteCount) {
    // 很长一段注释。。。goals: don't waste CPU and don't waste memory.
    if (source == null) throw new IllegalArgumentException("source == null");
    if (source == this) throw new IllegalArgumentException("source == this");
    checkOffsetAndCount(source.size, 0, byteCount);

    while (byteCount > 0) {
    
      // 这时候数据只在source当前segment中
      // 如果当前buffer最后一个segment能容纳这些数据，那就没有必要移动去移动segment了
      // 如果当前buffer最后一个segment容纳不了或者不能修改里面data，那还是得拼接过去
      // 而且因为source当前segment中可能还有不需要读的数据，要把这个segment按byteCount一分为二，前面部分拼接过去
      // Is a prefix of the source's head segment all that we need to move?
      if (byteCount < (source.head.limit - source.head.pos)) {
      
        // 双向链表，head的prev就是tail啊，写入要在尾部追加
        Segment tail = head != null ? head.prev : null;
        
        // tail(segment)是其data所有者，即可以修改data数据
        if (tail != null && tail.owner
        
            // 剩余空间加上byteCount后不超过segment最大长度，limit表示下一个可写位置，pos表示下一个可读位置
            // 如果当前segment前面部分share出去了，前面部分都不能写入(0 - limit)，没被share是不是可能segment前后都有空余空间？
            && (byteCount + tail.limit - (tail.shared ? 0 : tail.pos) <= Segment.SIZE)) {
          
          // Our existing segments are sufficient. Move bytes from source's head to our tail.
          source.head.writeTo(tail, (int) byteCount);   // 充裕的，可以写入
          source.size -= byteCount;
          size += byteCount;
          
          // byteCount在source和tail当前segment都允许的情况下，复制一下就行了，这里就over了
          return;
        } else {
            
          // 注意这里是在byteCount小于source当前segment大小的if里面，这里把source.head分成两个segment
          // 第一部分长byteCount，下面还在while中会进行处理
          // We're going to need another segment. Split the source's head
          // segment in two, then move the first of those two to this buffer.
          source.head = source.head.split((int) byteCount);
        }
      }

      // segment效率核心来了！直接把source的segment拼接到当前Buffer链表最后就行了
      // Remove the source's head segment and append it to our tail.
      Segment segmentToMove = source.head;
      long movedByteCount = segmentToMove.limit - segmentToMove.pos;
      
      // 从source中移除改segment
      source.head = segmentToMove.pop();
      
      if (head == null) {
        // head为空的时候直接作为链表头，首尾指针指向自己(形成闭环，tail和head都是自己)
        head = segmentToMove;
        head.next = head.prev = head;
      } else {
        // 在最后拼接就行
        Segment tail = head.prev;
        tail = tail.push(segmentToMove);
        
        // 消除一下拼接后中间的冗余，因为拼接后tail前一个的空余容量可能比tail的大小更大，这时候tail是不必要的，可以被回收
        tail.compact();
      }
      
      // 处理下大小记录，byteCount被修改会影响while循环
      source.size -= movedByteCount;
      size += movedByteCount;
      byteCount -= movedByteCount;
    }
  }
  
  // 默认不超时检测
  @Override public Timeout timeout() { return Timeout.NONE; }
```
Source的read方法很简单，Sink的timeout方法默认也是返回一个不超时检测的Timeout，也就Sink的write方法复杂些，作者还在前面加了很长的注释(大致就是让别浪费内存、CPU、合理利用segment之类的)，上面注释写的很清楚了，不过我还是想再流畅的简述下，便于理解。

当我们从一个buffer往另一个buffer里面写的时候，因为segment的存在，我们只要把segment从source的链表移动到sink的链表中去就行了，但是这要考虑下特殊情况，第一个是移动过去的segment可能可以与前一个segment合并，需要做下合并操作; 第二个就是byteCount比较小，直接就能复制到sink最后一个segment里面去; 第三个就是虽然byteCount比较小，可以复制过去，但是sink最后一个segment没有修改data数组的能力(!owner)，复制不过去，只能拼接过去; 第四个就是source的最后一个segment可能包含两部分数据，一部分要读取的，一部分不需要读取的，这时候就要将这个segment一分为二，把要读取的那部分移动过去就行了; 

看完这段代码，真就是牛逼！这个就是okio高效的核心吧。

### BufferedSource和BufferedSink的实现
上面有一节我们专门讲到了BufferedSource和BufferedSink，介绍了里面方法的功能，不过最后具体实现都是Buffer实现的，下面挑几个我觉得有意思的方法实现看下。

#### BufferedSource接口实现
##### readInt方法
从readXXX基本类型里面挑一个Int看一下，我觉得还是有代表性的:
```
@Override public int readInt() {
    // int型四字节
    if (size < 4) throw new IllegalStateException("size < 4: " + size);

    Segment segment = head;
    int pos = segment.pos;
    int limit = segment.limit;

    // If the int is split across multiple segments, delegate to readByte().
    if (limit - pos < 4) {
      // 不够四字节，降级分成四个一字节的byte读取，通过移位合成4byte的int，readByte里面会处理segment的切换
      return (readByte() & 0xff) << 24
          |  (readByte() & 0xff) << 16
          |  (readByte() & 0xff) <<  8
          |  (readByte() & 0xff);
    }

    // 从当前segment读取四个字节
    byte[] data = segment.data;
    int i = (data[pos++] & 0xff) << 24
        |   (data[pos++] & 0xff) << 16
        |   (data[pos++] & 0xff) <<  8
        |   (data[pos++] & 0xff);
    size -= 4;

    if (pos == limit) {
      // 刚好读完一个segment
      head = segment.pop();
      SegmentPool.recycle(segment);
    } else {
      segment.pos = pos;
    }

    return i;
  }
  
  // 高低位反向，在嵌入式中还是挺常见的吧
  @Override public int readIntLe() {
    return Util.reverseBytesInt(readInt());
  }
```
其实还是挺简单的，特别是和上面Sink的write方法比起来，不过还是要注意下segment的边界情况。

##### readDecimalLong方法
readDecimalLong方法是一个很有意思的方法，前面我们讲过了，会把数按十进制的字符串读取，下面看下实现:
```
@Override public long readDecimalLong() {
    if (size == 0) throw new IllegalStateException("size == 0");

    // This value is always built negatively in order to accommodate Long.MIN_VALUE.
    long value = 0;
    int seen = 0;
    boolean negative = false;
    boolean done = false;

    long overflowZone = Long.MIN_VALUE / 10;
    long overflowDigit = (Long.MIN_VALUE % 10) + 1;

    do {
      Segment segment = head;

      byte[] data = segment.data;
      int pos = segment.pos;
      int limit = segment.limit;

      for (; pos < limit; pos++, seen++) {
        byte b = data[pos];
        if (b >= '0' && b <= '9') {
          int digit = '0' - b;

          // Detect when the digit would cause an overflow.
          if (value < overflowZone || value == overflowZone && digit < overflowDigit) {
            Buffer buffer = new Buffer().writeDecimalLong(value).writeByte(b);
            if (!negative) buffer.readByte(); // Skip negative sign.
            throw new NumberFormatException("Number too large: " + buffer.readUtf8());
          }
          value *= 10;
          value += digit;
        } else if (b == '-' && seen == 0) {
          negative = true;
          overflowDigit -= 1;
        } else {
          if (seen == 0) {
            throw new NumberFormatException(
                "Expected leading [0-9] or '-' character but was 0x" + Integer.toHexString(b));
          }
          // Set a flag to stop iteration. We still need to run through segment updating below.
          done = true;
          break;
        }
      }

      if (pos == limit) {
        head = segment.pop();
        SegmentPool.recycle(segment);
      } else {
        segment.pos = pos;
      }
    } while (!done && head != null);

    size -= seen;
    return negative ? value : -value;
  }
```