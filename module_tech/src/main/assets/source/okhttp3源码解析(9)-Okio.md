# okhttp3源码解析(9)-Okio
## 前言
上两篇文章写了下okhttp3的缓存功能，里面用到了很多okio的内容，在网络请求的部分也用到了很多，我觉得还是有必要研究下的。一开始看okhttp3源码的时候，我也不知道怎么入手，后面发现不如直接就从最简单的使用开始，看到什么研究什么。下面的okio我也打算这么做。

其实我再写文章前找了些资料看了下，已经有作者写了很好的文章了，读者可以先看下别人的，图文并茂。

[Android IO 框架 Okio 的实现原理，到底哪里 OK？](https://juejin.cn/post/7167757174502850597)
[Android IO 框架 Okio 的实现原理，如何检测超时？](https://juejin.cn/post/7168097359807971342)
[值得一用的IO神器Okio](https://juejin.cn/post/6923902848908394510)

我这就只能说是自己看源码的理解了，比较生硬，比较无聊，仅供参考。

ps. 这篇文章时间跨度有点长了，中间慢慢写得，有点地方好啰嗦，有的地方又有点急了，写得也特别特别长，本来应该分好几篇文章的，建议按目录按需看。

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
- 内部类: UnsafeCursor，及四个获取UnsafeCursor的方法
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
    long value = 0;             // 值，一致按负数算，因为Long.MIN_VALUE绝对值大一
    int seen = 0;               // 当前读了几位
    boolean negative = false;   // 负数
    boolean done = false;

    // long 的范围 -2,147,483,648 到 2,147,483,647
    long overflowZone = Long.MIN_VALUE / 10;            // 加最后一位前如果溢出了，就不需要继续下去了
    long overflowDigit = (Long.MIN_VALUE % 10) + 1;     // 单个字符对应溢出的数字，即最后一位不能大于该数

    do {
      Segment segment = head;

      byte[] data = segment.data;
      int pos = segment.pos;
      int limit = segment.limit;

      for (; pos < limit; pos++, seen++) {
        byte b = data[pos];
        if (b >= '0' && b <= '9') {
        
          // 设计成取负数
          int digit = '0' - b;

          // 因为是负数，所以更小就是溢出了，溢出后拿了个新Buffer拼出字符串抛出异常，如果有负号，跳过负号对应字符
          // Detect when the digit would cause an overflow.
          if (value < overflowZone || value == overflowZone && digit < overflowDigit) {
            Buffer buffer = new Buffer().writeDecimalLong(value).writeByte(b);
            if (!negative) buffer.readByte(); // Skip negative sign.
            throw new NumberFormatException("Number too large: " + buffer.readUtf8());
          }
          
          value *= 10;
          value += digit;
        } else if (b == '-' && seen == 0) {
          // 第一个位置可能是负号，long负数的范围比正数范围大一
          negative = true;
          overflowDigit -= 1;
        } else {
          // 第0个位置出现了其他字符，直接就是异常
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
        // 读完一位
        segment.pos = pos;
      }
    } while (!done && head != null);

    // 减去读取的长度，根据负号标记对存在负数里的结果处理
    size -= seen;
    return negative ? value : -value;
  }
```
这里就是一个从字符串里读取long型数据的功能，里面验证数据范围的逻辑还是挺有意思的，这个负数和减一用的很巧妙。

##### read(byte[]...）方法
上面讲到了从ReadableByteChannel继承来的read(ByteBuffer)方法，但是对于Buffer里面大部分的read方法其实都是从下面方法得到的结果:
```
  @Override public int read(byte[] sink, int offset, int byteCount) {
    checkOffsetAndCount(sink.length, offset, byteCount);

    Segment s = head;
    if (s == null) return -1;
    
    // 得到head可以复制的容量
    int toCopy = Math.min(byteCount, s.limit - s.pos);
    System.arraycopy(s.data, s.pos, sink, offset, toCopy);

    s.pos += toCopy;
    size -= toCopy;

    if (s.pos == s.limit) {
      head = s.pop();
      SegmentPool.recycle(s);
    }

    return toCopy;
  }
```

##### indexOf方法
查找一般很考验算法，okio这里正好有一个，那还是得好好研究下。
```
@Override public long indexOf(ByteString bytes, long fromIndex) throws IOException {
    if (bytes.size() == 0) throw new IllegalArgumentException("bytes is empty");
    if (fromIndex < 0) throw new IllegalArgumentException("fromIndex < 0");

    Segment s;
    long offset;

    // TODO(jwilson): extract this to a shared helper method when can do so without allocating.
    findSegmentAndOffset: {
      // Pick the first segment to scan. This is the first segment with offset <= fromIndex.
      s = head;
      if (s == null) {
        // No segments to scan!
        return -1L;
        
        // 第一步，先找到对应fromIndex的segment，二分法，哪边更小从哪边操作
      } else if (size - fromIndex < fromIndex) {
        // We're scanning in the back half of this buffer. Find the segment starting at the back.
        offset = size;
        
        // 从最后一个segment开始算，offset从size开始减去这个segment的长度，如果offset更小了，s就是fromIndex对应的segment
        while (offset > fromIndex) {
          s = s.prev;
          offset -= (s.limit - s.pos);
        }
      } else {
        // We're scanning in the front half of this buffer. Find the segment starting at the front.
        offset = 0L;
        
        // 和前面类似，nextOffset每次加上一个segment长度，如果加上后比formIndex更大了，那s就是formIndex对应的segment
        for (long nextOffset; (nextOffset = offset + (s.limit - s.pos)) < fromIndex; ) {
          s = s.next;
          offset = nextOffset;
        }
      }
    }

    // 第二步，从对应的segment中寻找
    // Scan through the segments, searching for the lead byte. Each time that is found, delegate to
    // rangeEquals() to check for a complete match.
    byte b0 = bytes.getByte(0);
    int bytesSize = bytes.size();
    
    // 被查数据第一个字符所在位置的最大值，offset是当前segment第一个位置(指在Buffer中)
    long resultLimit = size - bytesSize + 1;
    
    // 第一层循环目的是让查找可以经过多个segment
    while (offset < resultLimit) {
      // Scan through the current segment.
      byte[] data = s.data;
      
      // 在该segment中的限制，第二个参数指在该segment中序号，那就不能大于segment长度
      int segmentLimit = (int) Math.min(s.limit, s.pos + resultLimit - offset);
      
      // 换算到segment内对应的pos(fromIndex - offset是偏移量，s.pos是segment内数据起始位置)
      for (int pos = (int) (s.pos + fromIndex - offset); pos < segmentLimit; pos++) {
      
        // 如果第一个字符匹配上了，就用rangeEquals去比对后面的数据，如果匹配上了就把在Buffer里面的位置返回回去
        // rangeEquals内会跨越segment进行查找
        if (data[pos] == b0 && rangeEquals(s, pos + 1, bytes, 1, bytesSize)) {
          // offset + (pos - s.pos)，offset是segment在Buffer的位置，加上数据在当前segment的便宜
          return pos - s.pos + offset;
        }
      }

      // Not in this segment. Try the next one.
      offset += (s.limit - s.pos);
      fromIndex = offset;
      s = s.next;
    }

    return -1L;
  }
```
这里看得有点头晕了，首先得明确Buffer中的位置(offset、fromIndex)和在segment中的位置(pos、limit)，首先根据fromIndex确定从哪个segment开始比较，确定后再使用segment中的位置进行比较第一位，第一位比较对上了，就调用rangeEquals进行下一步比较，它里面会支持跨segment比较，成功了会返回true or false，大致就是这样。

##### rangeEquals方法
既然上面提到了rangeEquals方法，我们这里也看下，比较简单:
```
private boolean rangeEquals(
      Segment segment, int segmentPos, ByteString bytes, int bytesOffset, int bytesLimit) {
    // 当前segment最后位置
    int segmentLimit = segment.limit;
    byte[] data = segment.data;

    // 比较到bytes最后一位就算成功了
    for (int i = bytesOffset; i < bytesLimit; ) {
      // 到达segment最后，进入下一个segment比较
      if (segmentPos == segmentLimit) {
        segment = segment.next;
        data = segment.data;
        segmentPos = segment.pos;
        segmentLimit = segment.limit;
      }

      if (data[segmentPos] != bytes.getByte(i)) {
        return false;
      }

      segmentPos++;
      i++;
    }

    return true;
  }
```
#### BufferedSink接口实现
看完BufferedSource接口的实现，我们再来看下BufferedSink接口的实现，和上面一样，我们选几个方法看下。

##### write(ByteString)方法
前面BufferedSource接口中的readByteString比较简单，我们直接跳过了，但是BufferedSink接口的ByteString还是有点东西的，值得一看:
```
  @Override public Buffer write(ByteString byteString) {
    if (byteString == null) throw new IllegalArgumentException("byteString == null");
    byteString.write(this);
    return this;
  }
  
  // ByteString内write方法
  void write(Buffer buffer) {
    buffer.write(data, 0, data.length);
  }
  
  // Buffer的方法
  @Override public Buffer write(byte[] source, int offset, int byteCount) {
    if (source == null) throw new IllegalArgumentException("source == null");
    checkOffsetAndCount(source.length, offset, byteCount);

    int limit = offset + byteCount;
    while (offset < limit) {
      Segment tail = writableSegment(1);

      int toCopy = Math.min(limit - offset, Segment.SIZE - tail.limit);
      System.arraycopy(source, offset, tail.data, tail.limit, toCopy);

      offset += toCopy;
      tail.limit += toCopy;
    }

    size += byteCount;
    return this;
  }
```
这里ByteString居然反过来又调用Buffer的方法进行写入，这不是多此一举么？其实还是有意义的，这里ByteString的数据没有向外提供，而是通过外部对象把自己数据写到外部，这样更安全吧。

##### Buffer write(byte[] source, offset, byteCount)方法
上面提到了Buffer的write(byte[] source, offset, byteCount)方法，这里也说一下，实际就是提供一个数组去写入，很实用。很多地方都是通过调用它实现功能的，比如RealBufferedSink中、outputStream方法中。

在okhttp源码中用了大量的write(byte[] source)，其最终实现也是在这里:
```
  @Override public Buffer write(byte[] source) {
    if (source == null) throw new IllegalArgumentException("source == null");
    return write(source, 0, source.length);
  }
```

##### writeUtf8和writeUtf8CodePoint方法
关于UTF-8的写入比较复杂，这里就用writeUtf8简单讲讲。我也是现找资料学的，比一定正确，但是能多学点东西总有好处:
```
@Override public Buffer writeUtf8(String string, int beginIndex, int endIndex) {
    // 异常处理。。。忽略
    
    // UTF-16任何字符对应的数字都用两个字节(65536)来保存，UTF-8有可能是用一个字节表示一个字符，也可能是两个，三个，最多四个
    // UTF-8: 0xxxxxxx(1byte,128,ascii码)，110xxxxx 10xxxxxx(2byte,2048)，1110xxxx 10xxxxxx 10xxxxxx(3byte,65536)
    // UTF-16没有标志位，容错性高; UTF-8常用于网络传输，UTF-16用于储存
    
    // Transcode a UTF-16 Java String to UTF-8 bytes. 将UTF-16转为UTF-8
    for (int i = beginIndex; i < endIndex;) {
      // charAt会按字符取，即c是两字节得UTF-16(65536)
      int c = string.charAt(i);

      // 转成1byte的UTF-8，即ASCII码
      if (c < 0x80) {
        Segment tail = writableSegment(1);
        byte[] data = tail.data;
        // 当前segment中当前字节得位置
        int segmentOffset = tail.limit - i;
        // 当前segment能容纳得字符个数
        int runLimit = Math.min(endIndex, Segment.SIZE - segmentOffset);

        // Emit a 7-bit character with 1 byte.
        data[segmentOffset + i++] = (byte) c; // 0xxxxxxx

        // 试着继续当ASCII码写，提高效率
        // Fast-path contiguous runs of ASCII characters. This is ugly, but yields a ~4x performance
        // improvement over independent calls to writeByte().
        while (i < runLimit) {
          c = string.charAt(i);
          if (c >= 0x80) break;
          data[segmentOffset + i++] = (byte) c; // 0xxxxxxx
        }

        int runSize = i + segmentOffset - tail.limit; // Equivalent to i - (previous i).
        tail.limit += runSize;
        size += runSize;

      // 两字节情况，[128, 2048)，将c写入到两个byte中去
      } else if (c < 0x800) {
        // xxxx xxx(x xx)(xx xxxx)
        // Emit a 11-bit character with 2 bytes.
        writeByte(c >>  6        | 0xc0); // 110xxxxx
        writeByte(c       & 0x3f | 0x80); // 10xxxxxx
        i++;

      // 三字节情况
      } else if (c < 0xd800 || c > 0xdfff) {
        // (xxxx) (xxxx xx)(xx xxxx)
        // Emit a 16-bit character with 3 bytes.
        writeByte(c >> 12        | 0xe0); // 1110xxxx
        writeByte(c >>  6 & 0x3f | 0x80); // 10xxxxxx
        writeByte(c       & 0x3f | 0x80); // 10xxxxxx
        i++;

      // 四个字节情况。
      // 如果字符是代理对，则它被编码为四个字节的序列。代理对是一对Unicode字符，用于表示UTF-16编码中BMP之外的字符。
      } else {
        // c is a surrogate. Make sure it is a high surrogate & that its successor is a low
        // surrogate. If not, the UTF-16 is invalid, in which case we emit a replacement character.
        int low = i + 1 < endIndex ? string.charAt(i + 1) : 0;
        if (c > 0xdbff || low < 0xdc00 || low > 0xdfff) {
          writeByte('?');
          i++;
          continue;
        }

        // UTF-16 high surrogate: 110110xxxxxxxxxx (10 bits)
        // UTF-16 low surrogate:  110111yyyyyyyyyy (10 bits)
        // Unicode code point:    00010000000000000000 + xxxxxxxxxxyyyyyyyyyy (21 bits)
        int codePoint = 0x010000 + ((c & ~0xd800) << 10 | low & ~0xdc00);

        // Emit a 21-bit character with 4 bytes.
        writeByte(codePoint >> 18        | 0xf0); // 11110xxx
        writeByte(codePoint >> 12 & 0x3f | 0x80); // 10xxxxxx
        writeByte(codePoint >>  6 & 0x3f | 0x80); // 10xxyyyy
        writeByte(codePoint       & 0x3f | 0x80); // 10yyyyyy
        i += 2;
      }
    }

    return this;
  }
```
有点懵逼，UTF-8的格式找了找资料，三个字节和四个字节的不是很清楚，但是大致功能好理解，就是把UTF-16的字符转成UTF-8的字符，使用charAt能得到字符的int型，然后根据范围去生成UTF-8，可能是一个字符，可能是两个字符，甚至三个四个字符，按格式生成字节就行了。

##### writeInt方法
对基本类型的写入比较简单，按字节写入segment的data里面就行了，没什么好说的。
```
@Override public Buffer writeInt(int i) {
    Segment tail = writableSegment(4);
    byte[] data = tail.data;
    int limit = tail.limit;
    data[limit++] = (byte) ((i >>> 24) & 0xff);
    data[limit++] = (byte) ((i >>> 16) & 0xff);
    data[limit++] = (byte) ((i >>>  8) & 0xff);
    data[limit++] = (byte)  (i         & 0xff);
    tail.limit = limit;
    size += 4;
    return this;
  }
```

##### writeDecimalLong方法
和前面readDecimalLong方法类似，
```
@Override public Buffer writeDecimalLong(long v) {
    if (v == 0) {
      // Both a shortcut and required since the following code can't handle zero.
      return writeByte('0');
    }

    boolean negative = false;
    if (v < 0) {
      v = -v;
      if (v < 0) { // Only true for Long.MIN_VALUE.
        return writeUtf8("-9223372036854775808");
      }
      negative = true;
    }

    // 也是厉害哦
    // Binary search for character width which favors matching lower numbers.
    int width = //
          v < 100000000L
        ? v < 10000L
        ? v < 100L
        ? v < 10L ? 1 : 2
        : v < 1000L ? 3 : 4
        : v < 1000000L
        ? v < 100000L ? 5 : 6
        : v < 10000000L ? 7 : 8
        : v < 1000000000000L
        ? v < 10000000000L
        ? v < 1000000000L ? 9 : 10
        : v < 100000000000L ? 11 : 12
        : v < 1000000000000000L
        ? v < 10000000000000L ? 13
        : v < 100000000000000L ? 14 : 15
        : v < 100000000000000000L
        ? v < 10000000000000000L ? 16 : 17
        : v < 1000000000000000000L ? 18 : 19;
    if (negative) {
      // 负数加一位符号位
      ++width;
    }

    Segment tail = writableSegment(width);
    byte[] data = tail.data;
    
    // 循环取余写入，最后写入符号位
    int pos = tail.limit + width; // We write backwards from right to left.
    while (v != 0) {
      int digit = (int) (v % 10);
      data[--pos] = DIGITS[digit];
      v /= 10;
    }
    if (negative) {
      data[--pos] = '-';
    }

    tail.limit += width;
    this.size += width;
    return this;
  }
```
看起来还说挺好理解的，获取长度那里虽然看起来长了点，好像还没什么好办法。

### Buffer自身方法
在上面已经对Buffer自身的方法做了个总结，大致如下: 
- 内部类: UnsafeCursor，及四个获取UnsafeCursor的方法
- 加密: md5、sha1等
- snapshot: 获得缓存数据的ByteString
- copy、write、read、getByte(pos)、clear、skip、rangeEquals，对缓存操作
- 获取参数: size、completeSegmentByteCount(segment剩余空间)、segmentSizes
- selectPrefix，判断前缀格式(配合options获得index)
- writableSegment(minimumCapacity)，获取可以够容量的segment

UnsafeCursor是这个Buffer的一个操作类，比较核心，还是得讲一下的。 

加密就跳过了，关于ByteString的内容可以简单讲讲，学下原理就可以了，内容好多。

对缓存的操作，不太想讲了，都是对segment的处理，在RealBufferedSource和RealBufferedSink以及Buffer内看得够多了。

获取参数的方法看一下就好，selectPrefix以及writableSegment还是可以看下的。

#### 内部类: UnsafeCursor，及四个获取UnsafeCursor的方法
先来看下UnsafeCursor自己的介绍吧，大致iiu是对缓存区基础数据的不安全处理器，这个安全问题需要用户自己注意，代码注释里面还有很长的例子，这里就不贴出来了。
> A handle to the underlying data in a buffer. This handle is unsafe because it does not enforce its own invariants. Instead, it assumes a careful user who has studied Okio's implementation details and their consequences.
> 缓冲区中基础数据的handle。该handle是不安全的，因为它不强制执行自己的不变量。相反，它假设一个细心的用户已经研究了 Okio 的实现细节及其后果。

##### 变量域
先来看下变量域，先来理解下这些成员变量代表的含义:
```
    // 控制的buffer
    public Buffer buffer;
    
    // 可读可写？
    public boolean readWrite;

    // 当前segment
    private Segment segment;
    
    // 当前偏移，最大为buffer.size
    public long offset = -1L;
    
    // references the segment's internal byte array
    public byte[] data;
    
    // is the segment's start，在buffer里面的位置
    public int start = -1;
    
    // is the segment's end，在buffer里面的位置
    public int end = -1;
```

下面再来理解方法，next和close比较简单就不写了。

##### seek方法
```
    public final int seek(long offset) {
      if (offset < -1 || offset > buffer.size) {
        throw new ArrayIndexOutOfBoundsException(
            String.format("offset=%s > size=%s", offset, buffer.size));
      }
      
      // 范围外
      if (offset == -1 || offset == buffer.size) {
        this.segment = null;
        this.offset = offset;
        this.data = null;
        this.start = -1;
        this.end = -1;
        return -1;
      }

      // Navigate to the segment that contains `offset`. Start from our current segment if possible.
      long min = 0L;
      long max = buffer.size;
      Segment head = buffer.head;
      Segment tail = buffer.head;
      if (this.segment != null) {
        // 获得当前segment第一个位置在buffer中的偏移
        long segmentOffset = this.offset - (this.start - this.segment.pos);
        
        // 根据当前segment位置修改min或max，缩小范围，以当前segment为界分两部分
        if (segmentOffset > offset) {
          // Set the cursor segment to be the 'end'
          max = segmentOffset;
          tail = this.segment;
        } else {
          // Set the cursor segment to be the 'beginning'
          min = segmentOffset;
          head = this.segment;
        }
      }

      // 从小的部分开始寻找offset所在segment
      Segment next;
      long nextOffset;
      if (max - offset > offset - min) {
        // Start at the 'beginning' and search forwards
        next = head;
        nextOffset = min;
        while (offset >= nextOffset + (next.limit - next.pos)) {
          nextOffset += (next.limit - next.pos);
          next = next.next;
        }
      } else {
        // Start at the 'end' and search backwards
        next = tail;
        nextOffset = max;
        while (nextOffset > offset) {
          next = next.prev;
          nextOffset -= (next.limit - next.pos);
        }
      }

      // If we're going to write and our segment is shared, swap it for a read-write one.
      if (readWrite && next.shared) {
        // 如果该segment被分享了，就复制一个不被分享的segment，并在下面代码代替原来的
        Segment unsharedNext = next.unsharedCopy();
        if (buffer.head == next) {
          buffer.head = unsharedNext;
        }
        next = next.push(unsharedNext);
        next.prev.pop();
      }

      // 更新Buffer的数据域
      // Update this cursor to the requested offset within the found segment.
      this.segment = next;
      this.offset = offset;
      this.data = next.data;
      this.start = next.pos + (int) (offset - nextOffset);
      this.end = next.limit;
      return end - start;
    }
```
seek方法不是很复杂，目的就是根据offset找到对应的segment，并在这个segment找到offset的位置，具体看上面注释。

##### resizeBuffer方法
```
public final long resizeBuffer(long newSize) {
      if (buffer == null) {
        throw new IllegalStateException("not attached to a buffer");
      }
      if (!readWrite) {
        throw new IllegalStateException("resizeBuffer() only permitted for read/write buffers");
      }

      long oldSize = buffer.size;
      if (newSize <= oldSize) {
        if (newSize < 0) {
          throw new IllegalArgumentException("newSize < 0: " + newSize);
        }
        
        // 从最后一个segment开始pop并回收，直到最后一个segment正好能容纳newSize
        // Shrink the buffer by either shrinking segments or removing them.
        for (long bytesToSubtract = oldSize - newSize; bytesToSubtract > 0; ) {
          Segment tail = buffer.head.prev;
          int tailSize = tail.limit - tail.pos;
          if (tailSize <= bytesToSubtract) {
          
            // tail被pop，返回的是tail的next，即head，可能为null
            buffer.head = tail.pop();
            SegmentPool.recycle(tail);
            bytesToSubtract -= tailSize;
          } else {
          
            // 把最后一个segment可写的位置缩到刚好newSize的位置
            tail.limit -= bytesToSubtract;
            break;
          }
        }
        // Seek to the end. 重置数据域
        this.segment = null;
        this.offset = newSize;
        this.data = null;
        this.start = -1;
        this.end = -1;
      } else if (newSize > oldSize) {
      
        // 逐个增加segment使之达到newSize要求
        // Enlarge the buffer by either enlarging segments or adding them.
        boolean needsToSeek = true;
        for (long bytesToAdd = newSize - oldSize; bytesToAdd > 0; ) {
        
          // 先验证最后一个segment能写入的大小，能完成bytesToAdd就添加bytesToAdd，否则就填满segment
          // writableSegment会验证是否写满了segment，不够会自动添加和切换到新segment
          Segment tail = buffer.writableSegment(1);
          int segmentBytesToAdd = (int) Math.min(bytesToAdd, Segment.SIZE - tail.limit);
          tail.limit += segmentBytesToAdd;
          bytesToAdd -= segmentBytesToAdd;

          // 只执行一次，一开始的时候跳到末尾segment
          // If this is the first segment we're adding, seek to it.
          if (needsToSeek) {
            this.segment = tail;
            this.offset = oldSize;
            this.data = tail.data;
            this.start = tail.limit - segmentBytesToAdd;
            this.end = tail.limit;
            needsToSeek = false;
          }
        }
      }

      buffer.size = newSize;

      return oldSize;
    }
```
resizeBuffer方法和它的名字一样，就是来对buffer大小进行控制的，会根据传入的size对segment进行回收或者创建。

##### expandBuffer方法
```
public final long expandBuffer(int minByteCount) {
      if (minByteCount <= 0) {
        throw new IllegalArgumentException("minByteCount <= 0: " + minByteCount);
      }
      if (minByteCount > Segment.SIZE) {
        throw new IllegalArgumentException("minByteCount > Segment.SIZE: " + minByteCount);
      }
      if (buffer == null) {
        throw new IllegalStateException("not attached to a buffer");
      }
      if (!readWrite) {
        throw new IllegalStateException("expandBuffer() only permitted for read/write buffers");
      }

      long oldSize = buffer.size;
      
      // 可能是原来最后一个segment也可能是新的segment，不过没关系
      Segment tail = buffer.writableSegment(minByteCount);
      // 最后一个segment能容纳的大小
      int result = Segment.SIZE - tail.limit;
      tail.limit = Segment.SIZE;
      // 实际扩容，result >= minByteCount
      buffer.size = oldSize + result;

      // Seek to the old size.
      this.segment = tail;
      
      // offset到达oldSize时，已经在新的segment了
      this.offset = oldSize;
      this.data = tail.data;
      this.start = Segment.SIZE - result;
      this.end = Segment.SIZE;

      return result;
    }
```
这里就是给一个minByteCount来扩容，看代码这个minByteCount应该需要比Segment.SIZE小，它会产生两种结果，一个就是原来的tail能够容纳minByteCount，就不用切换segment了，如果原来的tail容纳不了minByteCount，那就会进入到下一个segment，这时候原来tail后面可以写入的部分就会被跳过，所以当seek到oldSize的时候会到达新的segment起点处。

#### snapshot方法
```
  /** Returns an immutable copy of this buffer as a byte string. */
  public final ByteString snapshot() {
    if (size > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("size > Integer.MAX_VALUE: " + size);
    }
    return snapshot((int) size);
  }

  /**
   * Returns an immutable copy of the first {@code byteCount} bytes of this buffer as a byte string.
   */
  public final ByteString snapshot(int byteCount) {
    if (byteCount == 0) return ByteString.EMPTY;
    return new SegmentedByteString(this, byteCount);
  }
```
这里两个snapshot方法就是返回SegmentedByteString的，SegmentedByteString和ByteString内容还很多，这里就简单了解下功能，不做深入探讨。

> ByteString注释: 
> An immutable sequence of bytes. 
> Byte strings compare lexicographically as a sequence of unsigned bytes. That is, the byte string ff sorts after 00. This is counter to the sort order of the corresponding bytes, where -1 sorts before 0.
> 不可变的字节序列。
> 字节字符串按字典顺序比较为无符号字节序列。也就是说，字节字符串 ff 排序在 00 之后。这与相应字节的排序顺序相反，其中 -1 排序在 0 之前。

上面是ByteString自己的概述，简单来说就是ByteString是用来处理不可变的字节序列的，效率比较高，Buffer是用来处理可变序列的。ByteString内部使用byte数组储存数据，而不是用segment，提供了很多高效率的方法来对数据处理，比如加密(base64、md5、sha1等)、substring、rangeEquals、startsWith、indexOf、lastIndexOf等，感觉就是类似String吧。

> SegmentedByteString注释: 
> An immutable byte string composed of segments of byte arrays. This class exists to implement efficient snapshots of buffers. It is implemented as an array of segments, plus a directory in two halves that describes how the segments compose this byte string.
> 由字节数组段组成的不可变字节字符串。此类的存在是为了实现高效的缓冲区快照。它被实现为一个段数组，加上一个分为两半的目录，描述了段如何组成这个字节字符串。

这里只拿了SegmentedByteString前面部分的注释，大致意思就是它是一个有segment组成的不可变的字符串，它有两个数据域: segments用于保存数据，directory用来控制数据。segments是一个二维数组，directory是个一维数组，directory分为两部分，第一部分表示segments第一层数组中储存字符串的累加长度，第二部分表示segments第一层数组中储存字符串的起始位置，不是很好理解，这里用注释中的例子看一下就懂了:
> Suppose we have a byte string, [A, B, C, D, E, F, G, H, I, J, K, L, M]
> that is stored across three byte arrays: [x, x, x, x, A, B, C, D, E, x, x, x], [x, F, G], and [H, I, J, K, L, M, x, x, x, x, x, x]
> the complete directory is [5, 7, 13, 4, 1, 0].
> 前半部分表示每个array中累加的字符串长度，后半部分表示每个array中起始的位置
> 需要注意的是array的顺序是有序的，array中的数据是连续的

SegmentedByteString其他方法就一个toByteString需要看下，它会把数据转成ByteString，再由ByteString对外提供各种功能。

#### selectPrefix方法
前面BufferedSource中，我们讲到了select(Options options)方法，就是提供一个options数组，然后对每一行数据的前缀格式进行校验，然后返回校验到的options内i的index，当时那里的index就是通过Buffer来实现的。

selectPrefix方法比较长，还和Options有关，Options也比较长，这里就不贴代码了，但是其中的原理倒是很有学习作用，一起来看下吧！

##### Options类
Options类比较有意思，代码很长，但就做了两件事，一个是通过of创建Options，一个就是通过buildTrieRecursive递归生成trie树。

of方法会对传入的options进行排序去重，数据暂时保存在list里面，然后调用buildTrieRecursive去生成trie树，数据最终保存在trie树里面。这里最好先理解下trie树，它的根节点是空字符，然后从根节点可以根据前缀找到字符串，还挺实用的，用于搜索很方便。

buildTrieRecursive是一个递归的方法，能够将保存在list中的字符串转成trie树，保存的时候有一定的格式，原理差不多就这样，先来看下注释:
> Builds a trie encoded as an int array. Nodes in the trie are of two types: SELECT and SCAN. SELECT nodes are encoded as: - selectChoiceCount: the number of bytes to choose between (a positive int) - prefixIndex: the result index at the current position or -1 if the current position is not a result on its own - a sorted list of selectChoiceCount bytes to match against the input string - a heterogeneous list of selectChoiceCount result indexes (>= 0) or offsets (< 0) of the next node to follow. Elements in this list correspond to elements in the preceding list. Offsets are negative and must be multiplied by -1 before being used. SCAN nodes are encoded as: - scanByteCount: the number of bytes to match in sequence. This count is negative and must be multiplied by -1 before being used. - prefixIndex: the result index at the current position or -1 if the current position is not a result on its own - a list of scanByteCount bytes to match - nextStep: the result index (>= 0) or offset (< 0) of the next node to follow. Offsets are negative and must be multiplied by -1 before being used. This structure is used to improve locality and performance when selecting from a list of options.
> 构建一个编码为 int 数组的 trie。 trie 中的节点有两种类型：SELECT 和 SCAN。 SELECT 节点编码为： - selectChoiceCount：要选择的字节数（正整数） - prefixIndex：当前位置的结果索引，如果当前位置本身不是结果，则为 -1 - 排序列表要与输入字符串匹配的 selectChoiceCount 字节 - selectChoiceCount 结果索引 (>= 0) 或要跟随的下一个节点的偏移量 (< 0) 的异构列表。此列表中的元素对应于前面列表中的元素。偏移量为负数，使用前必须乘以 -1。 SCAN 节点编码为： - scanByteCount：按顺序匹配的字节数。该计数为负数，使用前必须乘以 -1。 - prefixIndex：当前位置的结果索引，如果当前位置本身不是结果，则为 -1 - 要匹配的 scanByteCount 字节列表 - nextStep：结果索引 (>= 0) 或偏移量 (< 0)要遵循的下一个节点。偏移量为负数，使用前必须乘以 -1。此结构用于提高从选项列表中进行选择时的局部性和性能。

本来不想看这个buildTrieRecursive方法的，但是想想好像学源码就是来学东西的，什么最能学到东西，不就是算法吗？所以还是研究下:
```
private static void buildTrieRecursive(
      long nodeOffset,                  // 在Buffer中的偏移
      Buffer node,                      // 要写入的Buffer
      int byteStringOffset,             // 在buffer中已经按顺序写入字符的偏移
      List<ByteString> byteStrings,     // 对应的options列表(经过排序和去重)
      int fromIndex,                    // 上面options列表开始坐标
      int toIndex,                      // 上面options列表结束坐标
      List<Integer> indexes)            // 上面options列表中每个位置在options中原始坐标index
  {
    if (fromIndex >= toIndex) throw new AssertionError();
    // byteStringOffset是buffer中已经按顺序写入字符的偏移，肯定不能超过该范围内的字符串长度还长
    // 已经写好: abc，范围内字符: abcdefg，abcg，abcf
    for (int i = fromIndex; i < toIndex; i++) {
      if (byteStrings.get(i).size() < byteStringOffset) throw new AssertionError();
    }

    ByteString from = byteStrings.get(fromIndex);
    ByteString to = byteStrings.get(toIndex - 1);
    int prefixIndex = -1;

    // 第一个字符串长度和偏移一样长，记录下index，跳过它看剩下的
    // If the first element is already matched, that's our prefix.
    if (byteStringOffset == from.size()) {
      prefixIndex = indexes.get(fromIndex);
      fromIndex++;
      from = byteStrings.get(fromIndex);
    }

    // 最后一个和第一个在byteStringOffset偏移上不相等，即有分叉
    if (from.getByte(byteStringOffset) != to.getByte(byteStringOffset)) {
      // If we have multiple bytes to choose from, encode a SELECT node.
      int selectChoiceCount = 1;
      
      // 因为已经排序好了，只要按顺序两两相比，不一样了就多了一种分支
      for (int i = fromIndex + 1; i < toIndex; i++) {
        if (byteStrings.get(i - 1).getByte(byteStringOffset)
            != byteStrings.get(i).getByte(byteStringOffset)) {
          selectChoiceCount++;
        }
      }

      // 计算子部分需要的长度，先往下看懂格式，intCount(node)可能是防止node中其他字符，我觉得是0，因为每次传过来的node都是新建的
      // 加2是下面两行，(selectChoiceCount * 2)表示每个节点和它对应的index
      // Compute the offset that childNodes will get when we append it to node.
      long childNodesOffset = nodeOffset + intCount(node) + 2 + (selectChoiceCount * 2);

      // 写入当前层的分支数，正数
      node.writeInt(selectChoiceCount);
      // 写入当前部分最符合的index
      node.writeInt(prefixIndex);

      // 写入第一个option，并按顺序写入分叉的option
      // 理解一下： 就是写入一层树结构，同一层的数据肯定是不一样的才会分叉，前面byteStringOffset一样的进入下一层
      for (int i = fromIndex; i < toIndex; i++) {
        byte rangeByte = byteStrings.get(i).getByte(byteStringOffset);
        if (i == fromIndex || rangeByte != byteStrings.get(i - 1).getByte(byteStringOffset)) {
          // 最后8位，即一个int
          node.writeInt(rangeByte & 0xff);
        }
      }

      // 下一层
      Buffer childNodes = new Buffer();
      int rangeStart = fromIndex;
      while (rangeStart < toIndex) {
        
        // 遍历一下，找到和rangeStart前byteStringOffset一致的数据，即[rangeStart, rangeEnd)
        byte rangeByte = byteStrings.get(rangeStart).getByte(byteStringOffset);
        int rangeEnd = toIndex;
        for (int i = rangeStart + 1; i < toIndex; i++) {
          if (rangeByte != byteStrings.get(i).getByte(byteStringOffset)) {
            rangeEnd = i;
            break;
          }
        }

        // 左闭右开，所以rangeStart就是最后一个，这里是递归出口
        if (rangeStart + 1 == rangeEnd
            && byteStringOffset + 1 == byteStrings.get(rangeStart).size()) {
          // The result is a single index.
          node.writeInt(indexes.get(rangeStart));
        } else {
        
          // 负一是格式，记录下偏移到什么地方，把上面计算到的某个节点的下一层递归
          // The result is another node.
          node.writeInt((int) (-1 * (childNodesOffset + intCount(childNodes))));
          
          // 注意(byteStringOffset + 1)，下一层的依据是下一个字符是否相同
          buildTrieRecursive(
              childNodesOffset,
              childNodes,
              byteStringOffset + 1,
              byteStrings,
              rangeStart,
              rangeEnd,
              indexes);
        }

        // 进入该层下一个节点的下面一层(前byteStringOffset一样的是一层)
        rangeStart = rangeEnd;
      }

      // 递归结束后，把下一层的数据写到当前层的Buffer里面
      node.write(childNodes, childNodes.size());

    } else {
      // 开始到结束在前byteStringOffset字符都相同，先找到相同的个数
      // If all of the bytes are the same, encode a SCAN node.
      int scanByteCount = 0;
      for (int i = byteStringOffset, max = Math.min(from.size(), to.size()); i < max; i++) {
        if (from.getByte(i) == to.getByte(i)) {
          scanByteCount++;
        } else {
          break;
        }
      }

      // 不是很理解，按下面写入的格式算的(2, scanByteCount, 1(最后if中两种情况都有加一))
      // intCount(node)表示什么？已有数据的长度，每次递归不都是新建的，等于0吗？保险起见？
      // Compute the offset that childNodes will get when we append it to node.
      long childNodesOffset = nodeOffset + intCount(node) + 2 + scanByteCount + 1;

      // 用负数写入相同的个数，和上面selectByteCount区别
      node.writeInt(-scanByteCount);
      node.writeInt(prefixIndex);

      // 写入前面相同的字符
      for (int i = byteStringOffset; i < byteStringOffset + scanByteCount; i++) {
        node.writeInt(from.getByte(i) & 0xff);
      }

      // 最后一个了
      if (fromIndex + 1 == toIndex) {
        // The result is a single index.
        if (byteStringOffset + scanByteCount != byteStrings.get(fromIndex).size()) {
          throw new AssertionError();
        }
        // 最后一个option对应的index
        node.writeInt(indexes.get(fromIndex));
      } else {
      
        // 跳过scanByteCount个数据后，后面剩下的给下一层处理
        // The result is another node.
        Buffer childNodes = new Buffer();
        node.writeInt((int) (-1 * (childNodesOffset + intCount(childNodes))));
        buildTrieRecursive(
            childNodesOffset,
            childNodes,
            byteStringOffset + scanByteCount,
            byteStrings,
            fromIndex,
            toIndex,
            indexes);
        node.write(childNodes, childNodes.size());
      }
    }
  }
```
加了很多注释还是不太好理解，大致来看就是按层遍历一个树，第一层就是对第一个字符的比较，以此类推，假设是第i层，就要对这层的数据比较第i位，每层分两种情况：这一层第i位都相同(甚至连续N层相同)，这层第i位不同(有j种不同，那该节点下一层就有j个节点)。总而言之，就是该节点后面不一样了就递归，最后把数据写入到总的一个buffer中。

大致就是这样，算法这东西说清楚有点难，还是代码好理解，上面的写入格式我也不太明白，不过没关系继续看的去，慢慢就懂了。

##### selectPrefix原理
看完了Options类，再回过来看selectPrefix方法，终于就不是一头雾水了，现在所有options数据都保存在了trie树里面，只要对options树进行遍历，按格式把数据读出来就行了。
```
int selectPrefix(Options options, boolean selectTruncated) {
    Segment head = this.head;
    if (head == null) {
      if (selectTruncated) return -2; // A result is present but truncated.
      return options.indexOf(ByteString.EMPTY);
    }

    Segment s = head;
    byte[] data = head.data;
    int pos = head.pos;
    int limit = head.limit;

    int[] trie = options.trie;
    int triePos = 0;

    int prefixIndex = -1;

    navigateTrie:
    while (true) {
      // Options中两种情况，n个字符连续相同、或者直接子树个数
      int scanOrSelect = trie[triePos++];

      // -1是初始化的默认值
      int possiblePrefixIndex = trie[triePos++];
      if (possiblePrefixIndex != -1) {
        prefixIndex = possiblePrefixIndex;
      }

      int nextStep;

      if (s == null) {
        break;
      } else if (scanOrSelect < 0) {
      
        // Scan模式，要先读取连续的一段字符串
        // Scan: take multiple bytes from the buffer and the trie, looking for any mismatch.
        int scanByteCount = -1 * scanOrSelect;
        int trieLimit = triePos + scanByteCount;
        
        // 对前面连续的scanByteCount个位置进行比较
        while (true) {
        
          // 取segment中数据进行比较
          int b = data[pos++] & 0xff;
          // 在scanByteCount中不一样了，就直接返回prefixIndex
          if (b != trie[triePos++]) return prefixIndex; // Fail 'cause we found a mismatch.
          boolean scanComplete = (triePos == trieLimit);

          // 切换segment
          // Advance to the next buffer segment if this one is exhausted.
          if (pos == limit) {
            s = s.next;
            pos = s.pos;
            data = s.data;
            limit = s.limit;
            
            // segment数据读取完了
            if (s == head) {
              if (!scanComplete) break navigateTrie; // We were exhausted before the scan completed.
              s = null; // We were exhausted at the end of the scan.
            }
          }
          
          // 在scanByteCount中全比对上了，就退出Scan这个代码块，进入外层下一个循环
          if (scanComplete) {
            // nextStep是下一个节点数据，而且肯定不是scan模式了，但是记录的是负的偏移
            // Options中: node.writeInt((int) (-1 * (childNodesOffset + intCount(childNodes))));
            nextStep = trie[triePos];
            break;
          }
        }
      } else {
      
        // Select模式，selectChoiceCount是分支树，后面的数据就是这一层的数据共selectChoiceCount个
        // Select: take one byte from the buffer and find a match in the trie.
        int selectChoiceCount = scanOrSelect;
        
        // 遍历这一层
        int b = data[pos++] & 0xff;
        int selectLimit = triePos + selectChoiceCount;
        while (true) {
          
          // 这一层都匹配失败了，那就失败了啊，没有必要继续了
          if (triePos == selectLimit) return prefixIndex; // Fail 'cause we didn't find a match.

          // 匹配到这一个分支，break进入下一层
          if (b == trie[triePos]) {
            // nextStep是下一层循环的偏移，是一个负数
            // Options中: node.writeInt((int) (-1 * (childNodesOffset + intCount(childNodes))));
            nextStep = trie[triePos + selectChoiceCount];
            break;
          }

          triePos++;
        }

        // Advance to the next buffer segment if this one is exhausted.
        if (pos == limit) {
          s = s.next;
          pos = s.pos;
          data = s.data;
          limit = s.limit;
          if (s == head) {
            s = null; // No more segments! The next trie node will be our last.
          }
        }
      }

      // 失败情况已经return，nextStep上面已经提到了是负数，进入下一层循环，大于等于0的时候就是结束的时候，存的index
      // Select时: node.writeInt(indexes.get(rangeStart));
      // Scan时: node.writeInt(indexes.get(fromIndex));
      if (nextStep >= 0) return nextStep; // Found a matching option.
      
      // 上面注释有，-nextStep就是下一个偏移所在位置
      triePos = -nextStep; // Found another node to continue the search.
    }

    // We break out of the loop above when we've exhausted the buffer without exhausting the trie.
    if (selectTruncated) return -2; // The buffer is a prefix of at least one option.
    return prefixIndex; // Return any matches we encountered while searching for a deeper match.
  }
```
麻了麻了，终于算理解清楚了。两种模式scan和select，格式如下: 
- scan第一位是该层分支个数(正数)，第二位是默认的prefixIndex(-1，树的最后一个就是正数index)，然后接该层的数据，再接上这一层的数据，最后一位两种情况:下一层的偏移、每最后一个节点的index。
- select第一位是要跳过字符的个数(负数用于区分)，第二位和上面一样，然后是要跳过的字符，没有数据，最后一位两种情况(下一个位置肯定是select模式): 下一层的偏移、每最后一个节点的index。

然后就是这两种模式的混合，构成了整个trie树。

#### writableSegment方法
writableSegment方法比起上面几个内容就简单多了，直接双向链表拿最后的segment，看看容量够不够，不够从SegmentPool拿个新的放最后并返回。
```
/**
   * Returns a tail segment that we can write at least {@code minimumCapacity}
   * bytes to, creating it if necessary.
   */
  Segment writableSegment(int minimumCapacity) {
    if (minimumCapacity < 1 || minimumCapacity > Segment.SIZE) throw new IllegalArgumentException();

    if (head == null) {
      head = SegmentPool.take(); // Acquire a first segment.
      return head.next = head.prev = head;
    }

    Segment tail = head.prev;
    if (tail.limit + minimumCapacity > Segment.SIZE || !tail.owner) {
      tail = tail.push(SegmentPool.take()); // Append a new empty segment to fill up.
    }
    return tail;
  }
```