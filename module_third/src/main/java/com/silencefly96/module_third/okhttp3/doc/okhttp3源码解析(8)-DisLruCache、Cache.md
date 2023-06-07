# okhttp3源码解析(8)-DiskLruCache、Cache
## 前言
上一篇文章写到了CacheStrategy和Cache的部分内容，其中Cache里面DiskLruCache涉及的东西很多，也只看了其中对InternalCache修饰的几个方法，接下来这篇文章就来讲讲DiskLruCache和Cache剩下的部分内容。

## DiskLruCache
我们先来看DiskLruCache，因为Cache剩下的部分内容几乎都是调用DiskLruCache实现的。下面我们先从构造函数开始，再到前面我们用到的函数作为入口，进行分析，最后再来看看其他方法及数据域。

### 构造方法
DiskLruCache的构造函数只有一个，东西还挺多的
```
  DiskLruCache(FileSystem fileSystem, File directory, int appVersion, int valueCount, long maxSize,
      Executor executor) {
    // 这个FileSystem是okhttp内的一个类，用来对文件进行操作，默认为FileSystem.SYSTEM
    this.fileSystem = fileSystem;
    this.directory = directory;
    this.appVersion = appVersion;
    
    // 三种日志，源码里面有很长一段注释可以看看
    this.journalFile = new File(directory, JOURNAL_FILE);
    this.journalFileTmp = new File(directory, JOURNAL_FILE_TEMP);
    this.journalFileBackup = new File(directory, JOURNAL_FILE_BACKUP);
    
    // the number of values per cache entry. Must be positive.
    // 这个值很重要，被标记为final(不能修改)，默认值是传递过来的ENTRY_COUNT = 2
    // 表示一个entry中的value数目，缓存数量 = valueCount * entry数量
    // 我一开始以为是valueCount是缓存数量，导致后面代码全理解错了。。。
    // ps. 再更新，还是理解错了，valueCount还不是entry中的缓存的个数，ENTRY_COUNT=2，第一部分是ENTRY_METADATA，
    // 第二部分是ENTRY_BODY，里面存了两个同等级的value，但是两个都是用来表示同一个缓存的。
    this.valueCount = valueCount;
    
    // the maximum number of bytes this cache should use to store
    // 这个也不是最大缓存数量，二十缓存空间的大小，bytes！
    this.maxSize = maxSize;
    
    // journal的线程池，单线程执行cleanupRunnable，用于日志处理
    this.executor = executor;
  }
```
构造函数包含了挺多信息，现在追踪一下它的调用链，它是在DiskLruCache中static的create方法中调用的:
```
  public static DiskLruCache create(FileSystem fileSystem, File directory, int appVersion,
      int valueCount, long maxSize) {
    //...
    // 这里创建了一个只有线程的线程池
    Executor executor = new ThreadPoolExecutor(0, 1, 60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<Runnable>(), Util.threadFactory("OkHttp DiskLruCache", true));

    return new DiskLruCache(fileSystem, directory, appVersion, valueCount, maxSize, executor);
  }
```
接下来就到了Cache方法内了:
```
  private static final int VERSION = 201105;
  private static final int ENTRY_COUNT = 2;
  
  public Cache(File directory, long maxSize) {
    this(directory, maxSize, FileSystem.SYSTEM);
  }

  Cache(File directory, long maxSize, FileSystem fileSystem) {
    this.cache = DiskLruCache.create(fileSystem, directory, VERSION, ENTRY_COUNT, maxSize);
  }
```
还记得前面说过Cache的来源吗？这里需要自己创建并传递到okhttp，所以至少directory和maxSize要自己设置，其他的倒是可以用默认的。

### cleanupRunnable
上面讲到了journal的线程池，正好这里看下，它就是用来执行cleanupRunnable的，下面看下cleanupRunnable的内容:
```
private final Runnable cleanupRunnable = new Runnable() {
    public void run() {
      synchronized (DiskLruCache.this) {
        if (!initialized | closed) { return; // Nothing to do }

        try {
          // size超出maxSize时进行处理
          trimToSize();
        } catch (IOException ignored) {
          mostRecentTrimFailed = true;
        }

        try {
          // 重建日志: 只有在将日志大小减半并消除至少 2000 次操作时才重建日志。
          if (journalRebuildRequired()) {
            rebuildJournal();
            redundantOpCount = 0;
          }
        } catch (IOException e) {
          mostRecentRebuildFailed = true;
          journalWriter = Okio.buffer(Okio.blackhole());
        }
      }
    }
  };
```
这里就两个主要功能，第一个是对Entry进行控制，第二个是对日志文件重建。下面看下trimToSize方法：
```
  void trimToSize() throws IOException {
    while (size > maxSize) {
      Entry toEvict = lruEntries.values().iterator().next();
      removeEntry(toEvict);
    }
    mostRecentTrimFailed = false;
  }
```
这里看起来好理解，就是从lruEntries中迭代，移除entry直到size符合要求，这里涉及DiskLruCache的Entry，我们后面再说。再看下重建日志的代码:
```
  // 只有在将日志大小减半并消除至少 2000 次操作时才重建日志。
  boolean journalRebuildRequired() {
    final int redundantOpCompactThreshold = 2000;
    return redundantOpCount >= redundantOpCompactThreshold
        && redundantOpCount >= lruEntries.size();
  }
  
  synchronized void rebuildJournal() throws IOException {
    if (journalWriter != null) {
      journalWriter.close();
    }

    // fileSystem就是用来管理文件的，这里直接打开journalFileTmp开写
    BufferedSink writer = Okio.buffer(fileSystem.sink(journalFileTmp));
    try {
      // 参考源码中对Journal格式的注释，按格式写入
      writer.writeUtf8(MAGIC).writeByte('\n');
      writer.writeUtf8(VERSION_1).writeByte('\n');
      writer.writeDecimalLong(appVersion).writeByte('\n');
      writer.writeDecimalLong(valueCount).writeByte('\n');
      writer.writeByte('\n');

      // 写入Entry
      for (Entry entry : lruEntries.values()) {
        if (entry.currentEditor != null) {
          // 还记录状态么，除了这里的DIRTY、CLEAN, 还有REMOVE、READ
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

    // 这里对原来额日志改到备份，再删除，将新的从temp改到journalFile，原来三个是这样的关系
    if (fileSystem.exists(journalFile)) {
      fileSystem.rename(journalFile, journalFileBackup);
    }
    fileSystem.rename(journalFileTmp, journalFile);
    fileSystem.delete(journalFileBackup);

    // journalFile换了，journalWriter自然要重新创建了
    journalWriter = newJournalWriter();
    // 修改下状态
    hasJournalErrors = false;
    mostRecentRebuildFailed = false;
  }
```
这里要先看下journal的格式，了解格式了，就清楚是如何重建journal的了，注意下里面三个文件的关系。到这我们先对journal告一段落，下面还有涉及。

### DiskLruCache.Entry
本来想先看DiskLruCache.Editor的，但是DiskLruCache.Editor里面都是对DiskLruCache.Entry的操作，这里就先来看DiskLruCache.Entry了。
```
private final class Entry {
    final String key;

    /** Lengths of this entry's files. */
    final long[] lengths;
    final File[] cleanFiles;
    final File[] dirtyFiles;

    /** True if this entry has ever been published. */
    boolean readable;

    /** The ongoing edit or null if this entry is not being edited. */
    Editor currentEditor;

    /** The sequence number of the most recently committed edit to this entry. */
    long sequenceNumber;

    Entry(String key) {
      this.key = key;

      // valueCount是entry中value的数量，默认是ENTRY_COUNT=2，第一部分是ENTRY_METADATA，第二部分是ENTRY_BODY
      // 因为一个entry有valueCount个value，所以这里不就应该创建valueCount个数据
      lengths = new long[valueCount];
      cleanFiles = new File[valueCount];
      dirtyFiles = new File[valueCount];

      // 名称是重复的，因此请重复使用相同的构建器以避免分配。
      StringBuilder fileBuilder = new StringBuilder(key).append('.');
      // 取上面的长度，后面每次循环都用上面长度的部分，后面的舍弃
      int truncateTo = fileBuilder.length();
      // 大致意思就是给每个数据都生成两个文件，一个以“.index”结尾的cleanFile，一个以“.index.tmp”结尾的脏文件
      for (int i = 0; i < valueCount; i++) {
        fileBuilder.append(i);
        cleanFiles[i] = new File(directory, fileBuilder.toString());
        fileBuilder.append(".tmp");
        dirtyFiles[i] = new File(directory, fileBuilder.toString());
        // 缩回到“key.”字符串
        fileBuilder.setLength(truncateTo);
      }
    }
    // ...
}
```
这里DiskLruCache.Entry构造方法中传入了一个key，然后创建了三个以valueCount(entry中value的数量，一个缓存默认分成两部分了)为长度的变量，并且为每个value都创建了两个文件，一个干净的文件，一个脏文件，每个Entry都用key创建 2 * valueCount(value数量) 个文件。

既然DiskLruCache.Entry是通过key创建的，里面有valueCount个数据(不是缓存哦)，但是key是通过request得到的，那意思是指valueCount个缓存都是同一个request的吗？确实是，response被分成两部分了，header部分和body部分。

下面内容不多，我们一次性看三个函数:
```
    // 就是给lengths赋值嘛，注意里面类型是long型，传进来的是String型，要转换下
    void setLengths(String[] strings) throws IOException {
      if (strings.length != valueCount) {
        throw invalidLengths(strings);
      }

      try {
        for (int i = 0; i < strings.length; i++) {
          lengths[i] = Long.parseLong(strings[i]);
        }
      } catch (NumberFormatException e) {
        throw invalidLengths(strings);
      }
    }
    
    /** Append space-prefixed lengths to {@code writer}. */
    void writeLengths(BufferedSink writer) throws IOException {
      // 输出每个length，上面重建Journal(rebuildJournal)时用到了，应该是Journal的一种格式
      for (long length : lengths) {
        writer.writeByte(' ').writeDecimalLong(length);
      }
    }

    // 带文本信息抛错
    private IOException invalidLengths(String[] strings) throws IOException {
      throw new IOException("unexpected journal line: " + Arrays.toString(strings));
    }
```
这个三个方法很简单，但是对我们理解DiskLruCache.Entry好像没什么帮助，后面还有一个方法:
```
    /**
     * Returns a snapshot of this entry. This opens all streams eagerly to guarantee that we see a
     * single published snapshot. If we opened streams lazily then the streams could come from
     * different edits.
     */
    // 返回此条目的快照。这会急切地打开所有流，以确保我们看到一个已发布的快照。如果我们延迟打开流，那么流可能来自不同的编辑。 
    Snapshot snapshot() {
      if (!Thread.holdsLock(DiskLruCache.this)) throw new AssertionError();

      // 根据上面的cleanFiles创建valueCount个Source，用来给Snapshot提供输出流
      Source[] sources = new Source[valueCount];
      long[] lengths = this.lengths.clone(); // Defensive copy since these can be zeroed out.
      try {
        for (int i = 0; i < valueCount; i++) {
          sources[i] = fileSystem.source(cleanFiles[i]);
        }
        return new Snapshot(key, sequenceNumber, sources, lengths);
      } catch (FileNotFoundException e) {
      
        // 处理异常，把sources全删除了
        // A file must have been deleted manually!
        for (int i = 0; i < valueCount; i++) {
          if (sources[i] != null) {
            Util.closeQuietly(sources[i]);
          } else {
            break;
          }
        }
        // Since the entry is no longer valid, remove it so the metadata is accurate (i.e. the cache
        // size.)
        try {
          // 这个entry没用了，进行移除操作
          removeEntry(this);
        } catch (IOException ignored) {
        }
        return null;
      }
    }
```
这里大致意思就是用Snapshot给entry向外提供一个快照吧。 Snapshot在上一篇文章已经简单介绍了下，大致就是一个数据类，里面最重要的就是可以通过edit方法得到一个editor来做操作，这个edit方法实际也是DiskLruCache里面的，后面单独一节介绍，现在看下这里出现的removeEntry:
```
boolean removeEntry(Entry entry) throws IOException {
    if (entry.currentEditor != null) {
      // 里面会删除entry所有dirtyFiles，并置空entry的currentEditor防止继续操作
      entry.currentEditor.detach(); // Prevent the edit from completing normally.
    }

    // 删除entry所有cleanFiles
    for (int i = 0; i < valueCount; i++) {
      fileSystem.delete(entry.cleanFiles[i]);
      // 这个size是总size吗？只包含cleanFiles？
      size -= entry.lengths[i];
      entry.lengths[i] = 0;
    }

    // 计数、记录、移除
    redundantOpCount++;
    journalWriter.writeUtf8(REMOVE).writeByte(' ').writeUtf8(entry.key).writeByte('\n');
    lruEntries.remove(entry.key);

    if (journalRebuildRequired()) {
      executor.execute(cleanupRunnable);
    }

    return true;
  }
```
这里和上面创建entry时创建了 2 * valueCount(value数量) 个文件一致，移除entry的时候把这些文件全删除了。虽然还是有点懵，但是对entry的功能算是了解了。

### DiskLruCache.Editor
在前面的Cache中我们见的最多的就是DiskLruCache.Editor和DiskLruCache.Snapshot了，下面就来看看DiskLruCache.Editor到底是干嘛的。这个类有点长，我们一点一点看：
```
/** Edits the values for an entry. */
public final class Editor {
    final Entry entry;
    final boolean[] written;
    private boolean done;

    Editor(Entry entry) {
      this.entry = entry;
      this.written = (entry.readable) ? null : new boolean[valueCount];
    }
    // ...
}
```
先来看构造方法，还真和上面的注释说的一样专门为一个entry进行编辑，这里的written也根据每个entry中的valueCount生成了一个数组。

下面我们继续看下DiskLruCache.Editor有那些函数:

#### detach方法
```
    // 阻止此编辑器正常完成。当编辑导致 I/O 错误时，或者当此编辑器处于活动状态时目标条目被逐出时，这是必需的。
    // 在任何一种情况下，我们都会删除编辑器创建的文件并阻止创建新文件。请注意，一旦分离了一个编辑器，另一个编辑器就可以编辑该条目。
    void detach() {
      if (entry.currentEditor == this) {
        for (int i = 0; i < valueCount; i++) {
          try {
            // 这里是删除了entry中所有的dirtyFile
            fileSystem.delete(entry.dirtyFiles[i]);
          } catch (IOException e) {
            // This file is potentially leaked. Not much we can do about that.
          }
        }
        // 修改currentEditor，防止继续操作
        entry.currentEditor = null;
      }
    }
```
这个垃圾机翻我没太看懂，大致意思就是异常的时候阻止entry继续工作吧，这个方法删除了entry的dirtyFiles，上面removeEntry用到了这个方法，那里属于正常移除，所以还会移除entry的cleanFiles。

#### newSource/newSink方法
newSource这个没什么解释的，就是按index拿到entry中cleanFiles的输入流，就是读取数据呗，
```
    // 返回一个无缓冲的输入流以读取最后提交的值，如果没有提交任何值则返回 null。
    public Source newSource(int index) {
      synchronized (DiskLruCache.this) {
        // 异常情况
        if (done) { throw new IllegalStateException(); }
        if (!entry.readable || entry.currentEditor != this) { return null; }
        
        try {
          // 获得entry中对于index的cleanFile的输入流
          return fileSystem.source(entry.cleanFiles[index]);
        } catch (FileNotFoundException e) {
          return null;
        }
      }
    }
```
上面是读，读的是cleanFiles，而下面是写，写的是dirtyFiles，那么问题来了，这不就是不同步了，不急看下面commit方法。
```
    // 返回一个新的无缓冲输出流以写入索引处的值。
    // 如果底层输出流在写入文件系统时遇到错误，则此编辑将在调用提交时中止。返回的输出流不会抛出 IOException。
    public Sink newSink(int index) {
      synchronized (DiskLruCache.this) {
        // 异常情况
        if (done) { throw new IllegalStateException(); }
        if (entry.currentEditor != this) { return Okio.blackhole(); }
        
        // 不是只读，所以可以写，然后标记上written(被写了)
        if (!entry.readable) { written[index] = true; }
        
        // 用dirtyFile写入
        File dirtyFile = entry.dirtyFiles[index];
        Sink sink;
        try {
          sink = fileSystem.sink(dirtyFile);
        } catch (FileNotFoundException e) {
          return Okio.blackhole(); // Returns a sink that writes nowhere.
        }
        
        // 也是cache包的一个类哦，对其中操作增加了对hasErrors判断，有error时不进行操作直接返回
        return new FaultHidingSink(sink) {
          @Override protected void onException(IOException e) {
            synchronized (DiskLruCache.this) {
              // 删除所有dirtyFiles，上面写的意思dirtyFiles嘛，不影响cleanFiles
              detach();
            }
          }
        };
      }
    }
```

#### commit/abort/abortUnlessCommitted 方法
是不是感觉理解到了点什么，是的entry将数据分成clear和dirty两种，就是用来区分读写的(类似CopyOnWriteArrayList)，读直接读clean数据，写得先将写dirty文件，再通过commit同步。
```
    // Commits this edit so it is visible to readers.
    // This releases the edit lock so another edit may be started on the same key.
    public void commit() throws IOException {
      synchronized (DiskLruCache.this) {
        // 原来done就是提交的标志啊
        if (done) { throw new IllegalStateException(); }
        
        if (entry.currentEditor == this) {
          // 完成提交
          completeEdit(this, true);
        }
        
        // 标记完成
        done = true;
      }
    }
```
英语注释很好理解，但是这个释放锁让其他地方修改同key的entry是什么意思呢？这里也没释放锁啊，看下completeEdit方法:
```
  synchronized void completeEdit(Editor editor, boolean success) throws IOException {
    Entry entry = editor.entry;
    if (entry.currentEditor != editor) {
      throw new IllegalStateException();
    }

    // success指正常情况，false时是失败处理，entry.readable什么意思？
    // If this edit is creating the entry for the first time, every index must have a value.
    if (success && !entry.readable) {
      for (int i = 0; i < valueCount; i++) {
        // 数据没有被写入，newSink会设置未true，循环里面写，每个都要写入了才能继续吗？
        if (!editor.written[i]) {
          editor.abort();
          throw new IllegalStateException("Newly created entry didn't create value for index " + i);
        }
        
        // 脏文件不存在，这里目的是dirtyFiles写入到cleanFiles，肯定不能不存在
        if (!fileSystem.exists(entry.dirtyFiles[i])) {
          editor.abort();
          return;
        }
      }
    }

    // 循环把dirtyFiles修改未cleanFiles，再把旧的cleanFiles删除，就完成了写入
    for (int i = 0; i < valueCount; i++) {
      File dirty = entry.dirtyFiles[i];
      if (success) {
        if (fileSystem.exists(dirty)) {
          File clean = entry.cleanFiles[i];
          fileSystem.rename(dirty, clean);
          long oldLength = entry.lengths[i];
          long newLength = fileSystem.size(clean);
          entry.lengths[i] = newLength;
          
          // 总size，缓存使用大小
          size = size - oldLength + newLength;
        }
      } else {
        // 所以success=false的时候是对失败情况进行处理对吧
        fileSystem.delete(dirty);
      }
    }

    redundantOpCount++;
    entry.currentEditor = null;
    // entry可读说明数据clean，上面success已经把dirty写到了clean
    if (entry.readable | success) {
      entry.readable = true;
      journalWriter.writeUtf8(CLEAN).writeByte(' ');
      journalWriter.writeUtf8(entry.key);
      entry.writeLengths(journalWriter);
      journalWriter.writeByte('\n');
      if (success) {
        entry.sequenceNumber = nextSequenceNumber++;
      }
    } else {
      // 不能读且success=false，就是把脏数据删除了而已
      lruEntries.remove(entry.key);
      journalWriter.writeUtf8(REMOVE).writeByte(' ');
      journalWriter.writeUtf8(entry.key);
      journalWriter.writeByte('\n');
    }
    journalWriter.flush();

    // 数据超大了，cleanupRunnable上面说了，两个作用：日志、trimToSize
    if (size > maxSize || journalRebuildRequired()) {
      executor.execute(cleanupRunnable);
    }
  }
```
completeEdit是DiskLruCache的方法，被标记成synchronized，所以所有对dirty数据写到clean数据是同步的。

上面代码很长，但是感觉就是对entry.readable和success的组合情况，按我理解啊，success指提交dirty的情况，false时是失败处理，readable为false的时候说明要写入了(理解有错，看下一段)，readable为true的时候说明数据本来就没问题(不需要写入)。

ps. 但是有个问题我还没理解，为什么entry要对valueCount个数据遍历，验证written[i]和dirtyFiles[i]，再结合英语注释和entry源码，哈哈，sorry，readable理解错了！readable=false的时候就是entry刚创建，readable更新为true就是在completeEdit代码这里。

看完提交，我们顺便看下对应的撤销提交，也是用completeEdit处理的:
```
  public void abort() throws IOException {
      synchronized (DiskLruCache.this) {
        if (done) {
          throw new IllegalStateException();
        }
        if (entry.currentEditor == this) {
          // 就是删除dirty文件
          completeEdit(this, false);
        }
        done = true;
      }
    }

    public void abortUnlessCommitted() {
      synchronized (DiskLruCache.this) {
        if (!done && entry.currentEditor == this) {
          try {
            // catch的就是上面的IllegalStateException吗？
            completeEdit(this, false);
          } catch (IOException ignored) {
          }
        }
      }
    }
```

## Cache和DiskLruCache交叉代码
上面把DiskLruCache的构造函数以及几个内部类解析了下，其中有关的部分也讲了下，但是DiskLruCache这东西就是给Cache用的，我们下面先从Cache上篇我们没讲到的、和DiskLruCache相关的代码讲起，然后深入到DiskLruCache里面，我感觉这样形式的理解可能会好一些。

### cache.get(key)方法
先来看get方法里的cache.get(key)，这里就是通过cache拿到一个snapshot:
```
  // Returns a snapshot of the entry named key, or null if it doesn't exist is not currently readable. 
  // If a value is returned, it is moved to the head of the LRU queue.
  public synchronized Snapshot get(String key) throws IOException {
    // 初始化
    initialize();

    // 有个变量closed控制cache是否关闭了，关闭了会抛出异常
    checkNotClosed();
    // 用正则验证key 
    validateKey(key);
    // 从lruEntries中取，是一个LinkedHashMap
    Entry entry = lruEntries.get(key);
    // !entry.readable表示entry刚创建
    if (entry == null || !entry.readable) return null;

    // 从entry生成snapshot
    Snapshot snapshot = entry.snapshot();
    if (snapshot == null) return null;

    redundantOpCount++;
    journalWriter.writeUtf8(READ).writeByte(' ').writeUtf8(key).writeByte('\n');
    if (journalRebuildRequired()) {
      executor.execute(cleanupRunnable);
    }

    return snapshot;
  }
```
这里的initialize后面几个方法再说，因为它也提供方法给Cache了。这里就是在lruEntries这个LinkedHashMap里面取值了，entry的snapshot方法上面也有说到。

### cache.edit(key)方法
在cache的put方法里面用用了cache.edit(key)方法，这里是为了得到一个Editor: 
```
  // Returns an editor for the entry named key, or null if another edit is in progress.
  public @Nullable Editor edit(String key) throws IOException {
    return edit(key, ANY_SEQUENCE_NUMBER);  // ANY_SEQUENCE_NUMBER = -1
  }

  synchronized Editor edit(String key, long expectedSequenceNumber) throws IOException {
    // 和get类似
    initialize();

    checkNotClosed();
    validateKey(key);
    Entry entry = lruEntries.get(key);
    
    // sequenceNumber是旧的所以过期了吗？
    if (expectedSequenceNumber != ANY_SEQUENCE_NUMBER && (entry == null
        || entry.sequenceNumber != expectedSequenceNumber)) {
      return null; // Snapshot is stale. 过期
    }
    
    // currentEditor在后面赋值，所以这里不为null，说明之前这个entry调用过edit得到Editor，并且还未将currentEditor置空
    if (entry != null && entry.currentEditor != null) {
      return null; // Another edit is in progress.
    }
    
    // 看英语吧
    if (mostRecentTrimFailed || mostRecentRebuildFailed) {
      // The OS has become our enemy! If the trim job failed, it means we are storing more data than
      // requested by the user. Do not allow edits so we do not go over that limit any further. If
      // the journal rebuild failed, the journal writer will not be active, meaning we will not be
      // able to record the edit, causing file leaks. In both cases, we want to retry the clean up
      // so we can get out of this state!
      executor.execute(cleanupRunnable);
      return null;
    }

    // Flush the journal before creating files to prevent file leaks.
    journalWriter.writeUtf8(DIRTY).writeByte(' ').writeUtf8(key).writeByte('\n');
    journalWriter.flush();

    if (hasJournalErrors) {
      return null; // Don't edit; the journal can't be written.
    }

    // 没有entry就需要新建，put操作会对应这样的情况，不只是取缓存
    if (entry == null) {
      entry = new Entry(key);
      lruEntries.put(key, entry);
    }
    
    // 生成editor，用来操作entry及其中文件
    Editor editor = new Editor(entry);
    entry.currentEditor = editor;
    return editor;
  }
```
这里和上面的get类似，主要目的就是根据key拿到entry，没有的话就创建，然后根据entry创建新的editor，给到外面，提供对缓存的操作。

### cache.remove(key) 方法
在Cache的remove方法里直接调用了DiskLruCache的remove方法，这里就不写了，看下DiskLruCache里面的remove:
```
  // Drops the entry for key if it exists and can be removed. If the entry for key is currently 
  // being edited, that edit will complete normally but its value will not be stored.
  public synchronized boolean remove(String key) throws IOException {
    initialize();

    checkNotClosed();
    validateKey(key);
    
    // 先取出entry，通过entry去remove
    Entry entry = lruEntries.get(key);
    if (entry == null) return false;
    // 这里还验证了下remove的结果
    boolean removed = removeEntry(entry);
    if (removed && size <= maxSize) mostRecentTrimFailed = false;
    return removed;
  }

  boolean removeEntry(Entry entry) throws IOException {
    // 防止移除的时候currentEditor还存在，即防止外部还能通过editor进行修改
    if (entry.currentEditor != null) {
      entry.currentEditor.detach(); // Prevent the edit from completing normally.
    }

    // 撒谎给你出所有cleanFile，并修改缓存大小size，为什么不删除dirtyFlie？
    // 注意上面entry.currentEditor.detach()里面把dirtyFlie删除了
    for (int i = 0; i < valueCount; i++) {
      fileSystem.delete(entry.cleanFiles[i]);
      size -= entry.lengths[i];
      entry.lengths[i] = 0;
    }

    redundantOpCount++;
    journalWriter.writeUtf8(REMOVE).writeByte(' ').writeUtf8(entry.key).writeByte('\n');
    lruEntries.remove(entry.key);

    if (journalRebuildRequired()) {
      executor.execute(cleanupRunnable);
    }

    return true;
  }
```
这里就是对缓存的清除，一个是对entry的创建的dirtyFile以及cleanFile的删除，另一个就是从lruEntries把entry移除。

### Cache的update方法
我们再来看下Cache的update:
```
  void update(Response cached, Response network) {
    Entry entry = new Entry(network);
    DiskLruCache.Snapshot snapshot = ((CacheResponseBody) cached.body()).snapshot;
    DiskLruCache.Editor editor = null;
    try {
      editor = snapshot.edit(); // Returns null if snapshot is not current.
      if (editor != null) {
        entry.writeTo(editor);
        editor.commit();
      }
    } catch (IOException e) {
      abortQuietly(editor);
    }
  }
```
这里没有通过DiskLruCache去拿editor，而是通过旧的Response去拿的，然后进行操作。

### abortQuietly
再来看Cache除了增删查改外第一个方法: 
```
  private void abortQuietly(@Nullable DiskLruCache.Editor editor) {
    // Give up because the cache cannot be written.
    try {
      if (editor != null) {
        editor.abort();
      }
    } catch (IOException ignored) {
    }
  }
```
就是用editor的abort嘛，上面刚说完。

### initialize
被给下面这么简单的代码忽悠了，实际这就是持有某个类去完成某件事，是代理模式吗？还是装饰模式？是代理吧。
```
  // Initialize the cache. This will include reading the journal files from the storage and building up the necessary in-memory cache information.
  // The initialization time may vary depending on the journal file size and the current actual cache size. The application needs to be aware of calling this function during the initialization phase and preferably in a background worker thread.
  // Note that if the application chooses to not call this method to initialize the cache. By default, the okhttp will perform lazy initialization upon the first usage of the cache.
  public void initialize() throws IOException {
    cache.initialize();
  }
```
在讲cache.initialize前，先看下这个方法的英语注释，大概就是这个不会主动调用，由应用自己决定，会耗时最好在线程中用，虽然Cache的initialize不会主动调用，但是DiskLruCache里面的initialize会在使用的时候自己初始化(上面get时就有)。

稍微注意下这个方法的使用，然后下面看看cache是怎么做的:
```
public synchronized void initialize() throws IOException {
    assert Thread.holdsLock(this);

    if (initialized) {
      return; // Already initialized.
    }

    // 处理journalFile，逻辑不难，稍微看下
    // If a bkp file exists, use it instead.
    if (fileSystem.exists(journalFileBackup)) {
      // If journal file also exists just delete backup file.
      if (fileSystem.exists(journalFile)) {
        fileSystem.delete(journalFileBackup);
      } else {
        fileSystem.rename(journalFileBackup, journalFile);
      }
    }

    // Prefer to pick up where we left off.
    if (fileSystem.exists(journalFile)) {
      try {
        // 读日志
        readJournal();
        // 处理日志
        processJournal();
        initialized = true;
        return;
      } catch (IOException journalIsCorrupt) {
        Platform.get().log(WARN, "DiskLruCache " + directory + " is corrupt: "
            + journalIsCorrupt.getMessage() + ", removing", journalIsCorrupt);
      }

      // 正常情况上面就结束了，下面是异常情况，日志读和处理出错了
      // The cache is corrupted, attempt to delete the contents of the directory. This can throw and
      // we'll let that propagate out as it likely means there is a severe filesystem problem.
      try {
        delete();
      } finally {
        closed = false;
      }
    }

    // 重建日志
    rebuildJournal();

    initialized = true;
  }
```
这里就是如果有journalFile，备份也行，然后就读取和处理下日志，如果没有或者异常了就走重建日志方法，重建我们上面已经讲了，下面看下读取和处理日志的方法:
```
private void readJournal() throws IOException {
    BufferedSource source = Okio.buffer(fileSystem.source(journalFile));
    try {
      // 按格式读取
      String magic = source.readUtf8LineStrict();
      String version = source.readUtf8LineStrict();
      String appVersionString = source.readUtf8LineStrict();
      String valueCountString = source.readUtf8LineStrict();
      String blank = source.readUtf8LineStrict();
      if (!MAGIC.equals(magic)
          || !VERSION_1.equals(version)
          || !Integer.toString(appVersion).equals(appVersionString)
          || !Integer.toString(valueCount).equals(valueCountString)
          || !"".equals(blank)) {
        throw new IOException("unexpected journal header: [" + magic + ", " + version + ", "
            + valueCountString + ", " + blank + "]");
      }

      // 就是算下行数吗？
      int lineCount = 0;
      while (true) {
        try {
          // 核心代码，差点看漏了
          readJournalLine(source.readUtf8LineStrict());
          lineCount++;
        } catch (EOFException endOfJournal) {
          break;
        }
      }
      // 得到冗余行数？
      redundantOpCount = lineCount - lruEntries.size();

      // If we ended on a truncated line, rebuild the journal before appending to it.
      if (!source.exhausted()) {
        // 读取到了结尾行，重建日志
        rebuildJournal();
      } else {
        // 日志更新，journalWriter需要新建
        journalWriter = newJournalWriter();
      }
    } finally {
      Util.closeQuietly(source);
    }
  }
```
上面方法忽略了核心方法，大致就是获得了个行数和冗余行数，读取有问题重建日志，那看下核心代码readJournalLine做了什么:
```
private void readJournalLine(String line) throws IOException {
    int firstSpace = line.indexOf(' ');
    if (firstSpace == -1) {
      throw new IOException("unexpected journal line: " + line);
    }

    int keyBegin = firstSpace + 1;
    int secondSpace = line.indexOf(' ', keyBegin);
    // key，应该就是对request的url的处理后字符串，在第一个和第二个空格中间，可以参考下DiskLruCache前面注释中的格式
    // 类似这样: 命令_key_ ，命令前一篇有: CLEAN、DIRTY、REMOVE、READ
    final String key;
    if (secondSpace == -1) {
      // 只有一个空格，所以后半部分就是key，但是命令是REMOVE的时候，这一行不需要
      key = line.substring(keyBegin);
      if (firstSpace == REMOVE.length() && line.startsWith(REMOVE)) {
        lruEntries.remove(key);
        return;
      }
    } else {
      key = line.substring(keyBegin, secondSpace);
    }
    
    // 在内存缓存中找，没有就新创建并放入lruEntries
    Entry entry = lruEntries.get(key);
    if (entry == null) {
      entry = new Entry(key);
      lruEntries.put(key, entry);
    }

    if (secondSpace != -1 && firstSpace == CLEAN.length() && line.startsWith(CLEAN)) {
      // 有效的数据，类似这样:CLEAN 3400330d1dfc7f3f7f4b8d4d803dfcf6 832 21054
      String[] parts = line.substring(secondSpace + 1).split(" ");
      entry.readable = true;        // entry可用
      entry.currentEditor = null;
      entry.setLengths(parts);      // 设置好长度数组，即第一部分(meta)长832，第二部分(body)长21054
      
    } else if (secondSpace == -1 && firstSpace == DIRTY.length() && line.startsWith(DIRTY)) {
      // 有脏数据就给一个currentEditor？所以是要去提交吗
      entry.currentEditor = new Editor(entry);
      
    } else if (secondSpace == -1 && firstSpace == READ.length() && line.startsWith(READ)) {
      // This work was already done by calling lruEntries.get().
    } else {
      throw new IOException("unexpected journal line: " + line);
    }
  }
```
这里就是对上面读取到的某一行，按着格式去处理，根据四种类型(CLEAN、DIRTY、REMOVE、READ)创建entry出来，补充信息，并存到内存缓存。所以这里的日志，并不只是对操作的记录，还是磁盘缓存和内存缓存的一种联系对吧。

下面继续看下处理:
```
  // Computes the initial size and collects garbage as a part of opening the cache. 
  // Dirty entries are assumed to be inconsistent and will be deleted.
  private void processJournal() throws IOException {
    fileSystem.delete(journalFileTmp);
    for (Iterator<Entry> i = lruEntries.values().iterator(); i.hasNext(); ) {
      Entry entry = i.next();
      // 这个方法只会在initialize里面，所以当currentEditor为空的时候就是新建的entry
      if (entry.currentEditor == null) {
        for (int t = 0; t < valueCount; t++) {
          size += entry.lengths[t];
        }
      } else {
        // currentEditor不为null，就是上面按行读取时对DIRTY数据设置的，需要删除
        entry.currentEditor = null;
        for (int t = 0; t < valueCount; t++) {
          fileSystem.delete(entry.cleanFiles[t]);
          fileSystem.delete(entry.dirtyFiles[t]);
        }
        i.remove();
      }
    }
  }
```
结合initialize的流程，它读取了Journal文件，并且读出了里面的格式信息、行信息，实际这里Journal不只是一个日志，还是对缓存的一个目录，时硬盘缓存到内存缓存的一种对应关系。

按行读取的Journal行信息，会创建一个个entry，读取的entry有两种状态: CLEAN和DIRTY，processJournal方法就是计算initialize时缓存的大小，并删除标记为DIRTY的数据。

这里再来理解下entry，entry包含缓存的一些信息(length、readable创建时不能read、currentEditor不为空的时候外部可能在使用)。它还控制着valueCount个数据，默认时两个，第一部分是缓存的header信息，第二部分是body信息，这两个数据都保存在文件里面，每个数据对应两个文件(cleanFile和dirtyFile)。

initialize的正常流程上面就结束了，不过还没完，异常的时候还有个delete方法，这个Cache里面也提供了对DiskLruCache的delete方法，所以我么们下一节看。

### delete方法
先看下Cache里面的delete方法，又是直接交给了DiskLruCache:
```
  // Closes the cache and deletes all of its stored values. 
  // This will delete all files in the cache directory including files that weren't created by the cache.
  public void delete() throws IOException {
    cache.delete();
  }
```
下面是DiskLruCache的delete方法:
```
  public void delete() throws IOException {
    close();
    // 创建Cache时传进来的目录，应该就是总的缓存目录
    fileSystem.deleteContents(directory);
  }
  
  // Closes this cache. Stored values will remain on the filesystem.
  @Override public synchronized void close() throws IOException {
    if (!initialized || closed) {
      closed = true;
      return;
    }
    // Copying for safe iteration. 复杂后再迭代关闭活动中的editor
    for (Entry entry : lruEntries.values().toArray(new Entry[lruEntries.size()])) {
      if (entry.currentEditor != null) {
        entry.currentEditor.abort();
      }
    }
    trimToSize();
    journalWriter.close();
    journalWriter = null;
    closed = true;
  }
```
close方法关闭了cache，并用closed标记缓存被关闭了，delete里面还用fileSystem删除了所有缓存目录，还真是够彻底。

### evictAll方法
这个方法就是删除所有的entry吧(内存缓存)，如果有正在编辑的entry(冲突的)，会完成操作，但是不会被保存。
```
  // Deletes all values stored in the cache. In-flight writes to the cache will complete normally, 
  // but the corresponding responses will not be stored.
  public void evictAll() throws IOException {
    cache.evictAll();
  }
```
下面是DiskLruCache的delete方法:
```
  // Deletes all stored values from the cache. 
  // In-flight edits will complete normally but their values will not be stored.
  public synchronized void evictAll() throws IOException {
    initialize();
    // Copying for safe iteration.
    for (Entry entry : lruEntries.values().toArray(new Entry[lruEntries.size()])) {
      removeEntry(entry);
    }
    mostRecentTrimFailed = false;
  }
```
这里要注意下removeEntry里面会删除entry保存在文件的dirtyFile和cleanFile，缓存的size也会被修改。

### urls方法
Cache的urls方法就是对DiskLruCache的snapshots的一个代理(或者说是适配器?)，里面会返回一个迭代器，具体逻辑通过DiskLruCache的snapshots返回的迭代器进行操作。
```
  // Returns an iterator over the URLs in this cache. 
  public Iterator<String> urls() throws IOException {
    return new Iterator<String>() {
      // 被代理的迭代器
      final Iterator<DiskLruCache.Snapshot> delegate = cache.snapshots();

      // 迭代器当前的值
      @Nullable String nextUrl;
      boolean canRemove;

      @Override public boolean hasNext() {
        // 已经调用过hasNext，并拿到值了
        if (nextUrl != null) return true;

        canRemove = false; // Prevent delegate.remove() on the wrong item!
        // 为什么这里要循环？好吧，里面有个return true跳出了循环
        while (delegate.hasNext()) {
          // 实际被代理的值
          DiskLruCache.Snapshot snapshot = delegate.next();
          try {
            // entry总value的第一部分，保存在文件里面
            BufferedSource metadata = Okio.buffer(snapshot.getSource(ENTRY_METADATA));
            nextUrl = metadata.readUtf8LineStrict();
            // 拿到值返回，否则继续循环
            return true;
          } catch (IOException ignored) {
            // We couldn't read the metadata for this snapshot; possibly because the host filesystem
            // has disappeared! Skip it.
          } finally {
            // 关闭文件流
            snapshot.close();
          }
        }

        return false;
      }

      @Override public String next() {
        if (!hasNext()) throw new NoSuchElementException();
        String result = nextUrl;
        // 注意拿到result后，对nextUrl置空了，以便可以继续使用hasnext方法
        nextUrl = null;
        // 调用了next才能remove，如果再调用hasNext会使canRemove成false，不能移除
        canRemove = true;
        return result;
      }

      @Override public void remove() {
        if (!canRemove) throw new IllegalStateException("remove() before next()");
        delegate.remove();
      }
    };
  }
```
下面看下DiskLruCache的snapshots方法:
```
public synchronized Iterator<Snapshot> snapshots() throws IOException {
    initialize();
    return new Iterator<Snapshot>() {
      // 复制了一份lruEntries的value，防止发生迭代的时候修改的问题(ConcurrentModificationException异常)
      /** Iterate a copy of the entries to defend against concurrent modification errors. */
      final Iterator<Entry> delegate = new ArrayList<>(lruEntries.values()).iterator();

      Snapshot nextSnapshot;
      Snapshot removeSnapshot;

      @Override public boolean hasNext() {
        if (nextSnapshot != null) return true;

        // 对象锁，处理的时候防止其他异步操作，主要就是对entry的修改
        synchronized (DiskLruCache.this) {
          // If the cache is closed, truncate the iterator.
          if (closed) return false;

          while (delegate.hasNext()) {
            Entry entry = delegate.next();
            Snapshot snapshot = entry.snapshot();
            if (snapshot == null) continue; // Evicted since we copied the entries.
            // 拿到结果，退出循环
            nextSnapshot = snapshot;
            return true;
          }
        }

        return false;
      }

      @Override public Snapshot next() {
        if (!hasNext()) throw new NoSuchElementException();
        removeSnapshot = nextSnapshot;
        nextSnapshot = null;
        // 当前值保存在removeSnapshot
        return removeSnapshot;
      }

      @Override public void remove() {
        if (removeSnapshot == null) throw new IllegalStateException("remove() before next()");
        try {
          // 移除缓存
          DiskLruCache.this.remove(removeSnapshot.key);
        } catch (IOException ignored) {
          // Nothing useful to do here. We failed to remove from the cache. Most likely that's
          // because we couldn't update the journal, but the cached entry will still be gone.
        } finally {
          removeSnapshot = null;
        }
      }
    };
  }
```
和上面的urls类似啊，不过这里是对lruEntries进行copy了一份再迭代的，如果你迭代的时候遇到过ConcurrentModificationException异常，那理解这个问题应该很简单。

### flush和close方法
上面其实讲的差不多了，内容好长了，也就剩下flush和close方法稍微有点东西，然后就是一些getter函数，flush和close方法都是直接调用DiskLruCache实现的，close方法上面其实已经讲了，我们稍微看下flush这个方法:
```
  /** Force buffered operations to the filesystem. */
  @Override public synchronized void flush() throws IOException {
    if (!initialized) return;

    checkNotClosed();
    trimToSize();
    journalWriter.flush();
  }
```
好吧就是调用了下trimToSize，并对journalWriter刷新，就是强制把buffer写入到文件里面。

### 一些getter方法
最后剩下的一些Cache和DiskLruCache相关的方法就是一些getter方法了，有: size、maxSize、directory、isClosed，不用多说。

## 小结
这里用Android Studio摸鱼写的，居然一千一百行了，也终于差不多全讲完了，这里稍微小结下，真的只有学完才能最终理解好内容。

其实Cache就是和DiskLruCache的装饰模式，是对response的一个缓存，其中有内存缓存lruEntries，也有磁盘缓存，缓存由DiskLruCache的entry控制，entry的修改通过editor处理，DiskLruCache的entry不向外公开，一般对外提供一个Snapshot，Snapshot包含一些信息，并能够提供editor和磁盘缓存流。entry内对response的保存分两部分，一部分保存ENTRY_METADATA(即header信息)，一部分保存在ENTRY_BODY(即body信息)，这两部分都会保存到文件里面，并且会有两个文件(dirtyFile和cleanFile)，读写分离。DiskLruCache有一个日志系统(journal)，它不仅仅是对操作的记录，还会参与磁盘缓存到内存缓存读取的逻辑，与其说是日志，更像是一些控制信息。

好了，大致就是这样，写完真不容易。