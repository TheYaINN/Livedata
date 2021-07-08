package livedata;

import java.util.ArrayList;
import java.util.List;

public class Person_impl implements PersonDAO {

    private DataBase dataBase;

    List<Person> test = new ArrayList<>();

    Person_impl(DataBase dataBase) {
        this.dataBase = dataBase;
    }

    @Override
    public void insert(Person person) {
        System.out.println("Inserting: " + person);
        test.add(person);
    }

    @Override
    public LiveData<List<Person>> getPeople() {
        return new ComputableLiveData<List<Person>>(dataBase.getQueryExecutor()) {
            @Override
            protected List<Person> compute() {
                return test;
            }
        }.getLiveData();
    }

}
