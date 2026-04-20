build: Tester.class

run: build
	java -cp src/ Tester

test: build
	java -cp src/ Tester --test

Tester.class: src/*.class
	javac src/*.java

test.gb:
	dev/build.sh dev/test

src/*.class:
