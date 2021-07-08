package livedata;

public class PersonDataBaseConnector implements DataBaseConnector<PersonDAO> {

    private PersonDAO personDAO;

    PersonDataBaseConnector() {
        personDAO = new Person_impl(() -> Runnable::run);
    }

    @Override
    public PersonDAO getConnection() {
        return personDAO;
    }
}
