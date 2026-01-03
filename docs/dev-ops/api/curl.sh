curl http://localhost:11434/api/chat -d '{
  "model": "deepseek-r1:1.5b",
  "messages": [{
    "role": "user",
    "content": "Hello there!"
  }],
  "stream": false
}'
