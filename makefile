build: Tester.class test.gb

run: build
	java -cp src/ Tester

Tester.class: src/*.class
	javac src/*.java

test.gb:
	dev/build.sh dev/test

src/*.class:

