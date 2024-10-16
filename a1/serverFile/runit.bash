#!/bin/bash

# mkdir /virtual/$USER
# rm /virtual/$USER/example.db
javac URLShortner.java
javac URLShortnerThread.java
javac Cache.java
java URLShortner &
