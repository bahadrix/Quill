Quill
=====

Library for easy and powerful Java multi-threading in approach of Hadoop and CUDA

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
final ConcurrentMap<Integer, AtomicInteger> words = new ConcurrentSkipListMap<Integer, AtomicInteger>();

// Create factory and job implementation
QuillFactory<String, String> factory = 
  new QuillFactory<String, String>(200,20, new QuillFactory.IJob<String, String>() {
  	
  	@Override
  	public void onBlockReceived(Iterator<String> iterator, 
  	                BlockingQueue<String> output, String workerName) {
  		String line;
  		Integer tempLen;
  
  		while(iterator.hasNext()) {
  
  			line = iterator.next();
  			StringTokenizer tokens = new StringTokenizer(line);
  			
  			while(tokens.hasMoreTokens()) {
  				tempLen = tokens.nextToken().length();
  				words.putIfAbsent(tempLen, new AtomicInteger(0));
  				words.get(tempLen).incrementAndGet();
  			}
  			
  		}
  	}
});

File sampleTextFile = new File("sample.txt");
BufferedReader br = new BufferedReader(new FileReader(sampleTextFile));

String line;
while ((line = br.readLine()) != null) {
	factory.commit(line);
}

br.close();
factory.close(); // wait until complete

// Print results
for(Integer wordlen : words.keySet()) {

	System.out.println(String.format("%d: %d", wordlen, words.get(wordlen).get()));
}

```
