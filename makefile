run: Test.class test.gb
	java -cp src/ Test

Test.class: src/*.class
	javac src/*.java

test.gb:
	dev/build.sh dev/test
