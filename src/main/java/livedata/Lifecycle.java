package livedata;

public abstract class Lifecycle {

    public abstract State getCurrentState();

    public abstract void addObserver(LifecycleObserver observer);

    public abstract void removeObserver(LifecycleObserver observer);

    public enum Event {
        ON_CREATE,
        ON_RESUME,
        ON_PAUSE,
        ON_DESTROY,
    }


    public enum State {
        DESTROYED,
        INITIALIZED,
        CREATED,
        STARTED,
        RESUMED;

        public boolean isAtLeast(State state) {
            return compareTo(state) >= 0;
        }
    }

}
