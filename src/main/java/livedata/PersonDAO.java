package livedata;

import java.util.List;

public interface PersonDAO {

    void insert(Person person);

    LiveData<List<Person>> getPeople();
}
