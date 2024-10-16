#!/bin/bash 
javac LoadTest.java 
echo $SECONDS 
java LoadTest 127.0.0.1 8080 11 PUT 1000 & 
java LoadTest 127.0.0.1 8080 12 PUT 1000 & 
java LoadTest 127.0.0.1 8080 13 PUT 1000 & 
java LoadTest 127.0.0.1 8080 14 PUT 1000 & 
wait $(jobs -p) 
echo $SECONDS
