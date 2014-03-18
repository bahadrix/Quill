Quill
=====

Light-weight library for easy and powerful Java multi-threading in approach of Hadoop and CUDA


## Maven Dependency
```xml
<repositories>
	<repository>
	    <id>repo.bahadir.me</id>
	    <name>Bahadir's Repo</name>
	    <url>http://repo.bahadir.me</url>
	</repository>
</repositories>

<dependencies>
        <dependency>
            <groupId>me.bahadir</groupId>
            <artifactId>Quill</artifactId>
            <version>0.8</version>
        </dependency>
</dependencies>
```

## Examples
### Word Count by Length

Counts words by their lengths. 
e.g. "Quick brown fox and friends"
```java
5: 2
3: 2
7: 1
```
####Code
```java

// Output map
final ConcurrentMap<Integer, AtomicInteger> words = 
	new ConcurrentSkipListMap<Integer, AtomicInteger>();

// Each block consists of 200 lines
final int blockSize = 200;

// Number of total factory workers
final int workerSize = 20;

// Create factory and job implementation
QuillFactory<String, String> factory = 
  new QuillFactory<String, String>(blockSize, workerSize, new QuillFactory.IJob<String, String>() {
  	
  	@Override
  	public void onBlockReceived(Iterator<String> iterator, 
  	                BlockingQueue<String> output, String workerName) {
  		String line;
  		Integer tempLen;
  
  		while(iterator.hasNext()) { // for each line in block
  
  			line = iterator.next();
  			StringTokenizer tokens = new StringTokenizer(line);
  			
  			while(tokens.hasMoreTokens()) { // for each word in line
  				tempLen = tokens.nextToken().length();
  				words.putIfAbsent(tempLen, new AtomicInteger(0));
  				words.get(tempLen).incrementAndGet();
  			}
  			
  		}
  	}
});

//Read the sample file
File sampleTextFile = new File("sample.txt");
BufferedReader br = new BufferedReader(new FileReader(sampleTextFile));


String line;
while ((line = br.readLine()) != null) {
	// Pass each line to the factory.
	// When the first block has filled then the factory starts working.
	factory.commit(line);
}

br.close();

// Close the factory. Must be called!
factory.close(); // Wait until complete

// Print results
for(Integer wordlen : words.keySet()) {
	System.out.println(String.format("%d: %d", wordlen, words.get(wordlen).get()));
}

```
