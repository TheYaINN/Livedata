package livedata;

import javax.swing.*;
import java.awt.*;

public class GUI implements LifecycleOwner {

    LifecycleRegistry registry;

    GUI() throws InterruptedException {
        registry = new LifecycleRegistry(this);
        PersonDataBaseConnector dataBaseConnector = new PersonDataBaseConnector();

        JFrame frame = new JFrame("test");
        frame.setLayout(new BorderLayout());
        frame.setSize(200, 200);
        JButton test = new JButton("Test");
        frame.add(test, BorderLayout.CENTER);
        test.addActionListener(e -> dataBaseConnector.getConnection().insert(new Person("Test2", "3cm")));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);


        dataBaseConnector.getConnection().getPeople().observe(this, System.out::println);
        dataBaseConnector.getConnection().insert(new Person("Test", "3cm"));

        new Runnable() {
            @Override
            public void run() {
                dataBaseConnector.getConnection().insert(new Person("Test3", "3cm"));
            }
        };

        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE);
    }

    @Override
    public Lifecycle getLifecycle() {
        return registry;
    }

    public static void main(String[] args) throws InterruptedException {
        new GUI();
    }

}