# askHr
1. run qdrant vector database
  docker run -d --name qdrant -p 6333:6333 -p 6334:6334 -v C:\Users\273273\desktop\project/qdrant_storage:/qdrant/storage qdrant/qdrant

 2. This also should be there 
  ollama pull mxbai-embed-large
  ollama list  //You must see: mxbai-embed-large
