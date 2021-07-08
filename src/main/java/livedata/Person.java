package livedata;

public class Person {

    String name;
    String height;

    public Person(String name, String height) {
        this.name = name;
        this.height = height;
    }

    @Override
    public String toString() {
        return "Name: " + name + ", height: " + height;
    }
}