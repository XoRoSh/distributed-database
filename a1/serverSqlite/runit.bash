#!/bin/bash
USER="`whoami`"
mkdir /virtual/$USER 2> /dev/null
rm /virtual/$USER/database.db
sqlite3 /virtual/$USER/database.db < schema.sql

javac URLShortner.java
java -classpath ".:sqlite-jdbc-3.39.3.0.jar" URLShortner &



