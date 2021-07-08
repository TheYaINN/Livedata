package livedata;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

public class LifecycleRegistry extends Lifecycle {

    private State state;
    private WeakReference<LifecycleOwner> owner;
    private SafeIterable<LifecycleObserver, StateFulObserver> observerMap = new SafeIterable<>();
    private boolean isHandlingEvent;
    private boolean newEventOccurred;
    private ArrayList<State> parentStates = new ArrayList<>();


    public LifecycleRegistry(LifecycleOwner provider) {
        this.owner = new WeakReference<>(provider);
        state = State.INITIALIZED;
    }

    @Override
    public State getCurrentState() {
        return state;
    }

    @Override
    public void addObserver(LifecycleObserver observer) {
        State initialState = state == State.DESTROYED ? State.DESTROYED : State.INITIALIZED;
        StateFulObserver stateFulObserver = new StateFulObserver(observer, initialState);
        observerMap.putIfAbsent(observer, stateFulObserver);
        //TODO
    }

    @Override
    public void removeObserver(LifecycleObserver observer) {
        observerMap.remove(observer);
    }

    public void handleLifecycleEvent(Lifecycle.Event event) {
        State next = getStateAfter(event);
        moveToState(next);
    }

    private void popParentState() {
        parentStates.remove(parentStates.size() - 1);
    }

    private void pushParentState(State state) {
        parentStates.add(state);
    }

    private void moveToState(State next) {
        if (state == next) {
            return;
        }
        state = next;
        if (isHandlingEvent) {
            newEventOccurred = true;
            return;
        }
        isHandlingEvent = true;
        sync();
        isHandlingEvent = false;
    }

    private void sync() {
        LifecycleOwner lifecycleOwner = owner.get();
        if (lifecycleOwner == null) {
            throw new IllegalStateException("livedata.LifecycleOwner of this livedata.LifecycleRegistry is already"
                    + "garbage collected. It is too late to change lifecycle state.");
        }
        while (!isSynced()) {
            newEventOccurred = false;
            if (state.compareTo(observerMap.eldest().getValue().state) < 0) {
                backwardPass(lifecycleOwner);
            }
            Map.Entry<LifecycleObserver, StateFulObserver> newest = observerMap.newest();
            if (!newEventOccurred && newest != null && state.compareTo(newest.getValue().state) > 0) {
                forwardPass(lifecycleOwner);
            }
        }
        newEventOccurred = false;
    }

    private boolean isSynced() {
        if (observerMap.size() == 0) {
            return true;
        }
        State eldestObserverState = observerMap.eldest().getValue().state;
        State newestObserverState = observerMap.newest().getValue().state;
        return eldestObserverState == newestObserverState && state == newestObserverState;
    }

    private void forwardPass(LifecycleOwner lifecycleOwner) {
        Iterator<Map.Entry<LifecycleObserver, StateFulObserver>> ascendingIterator = observerMap.iteratorWithAdditions();
        while (ascendingIterator.hasNext() && !newEventOccurred) {
            Map.Entry<LifecycleObserver, StateFulObserver> entry = ascendingIterator.next();
            StateFulObserver observer = entry.getValue();
            while ((observer.state.compareTo(state) < 0 && !newEventOccurred && observerMap.contains(entry.getKey()))) {
                pushParentState(observer.state);
                observer.dispatchEvent(lifecycleOwner, upEvent(observer.state));
                popParentState();
            }
        }
    }

    private void backwardPass(LifecycleOwner lifecycleOwner) {
        Iterator<Map.Entry<LifecycleObserver, StateFulObserver>> descendingIterator =
                observerMap.descendingIterator();
        while (descendingIterator.hasNext() && !newEventOccurred) {
            Map.Entry<LifecycleObserver, StateFulObserver> entry = descendingIterator.next();
            StateFulObserver observer = entry.getValue();
            while ((observer.state.compareTo(state) > 0 && !newEventOccurred && observerMap.contains(entry.getKey()))) {
                Event event = downEvent(observer.state);
                pushParentState(getStateAfter(event));
                observer.dispatchEvent(lifecycleOwner, event);
                popParentState();
            }
        }
    }


    static State getStateAfter(Event event) {
        switch (event) {
            case ON_CREATE:
                return State.CREATED;
            case ON_PAUSE:
                return State.STARTED;
            case ON_RESUME:
                return State.RESUMED;
            case ON_DESTROY:
                return State.DESTROYED;
        }
        throw new IllegalArgumentException("Unexpected event value " + event);
    }

    private static Event downEvent(State state) {
        switch (state) {
            case INITIALIZED:
            case DESTROYED:
                throw new IllegalArgumentException();
            case CREATED:
            case STARTED:
                return Event.ON_DESTROY;
            case RESUMED:
                return Event.ON_PAUSE;
        }
        throw new IllegalArgumentException("Unexpected state value " + state);
    }

    private static Event upEvent(State state) {
        switch (state) {
            case INITIALIZED:
            case DESTROYED:
            case CREATED:
                return Event.ON_CREATE;
            case STARTED:
                return Event.ON_RESUME;
            case RESUMED:
                throw new IllegalArgumentException();
        }
        throw new IllegalArgumentException("Unexpected state value " + state);
    }

    static State min(State state1, State state2) {
        return state2 != null && state2.compareTo(state1) < 0 ? state2 : state1;
    }

    static class StateFulObserver {

        private LifecycleEventObserver lifecycleObserver;
        private State state;

        public StateFulObserver(LifecycleObserver observer, State initialState) {
            lifecycleObserver = (LifecycleEventObserver) observer;
            this.state = initialState;
        }

        void dispatchEvent(LifecycleOwner owner, Event event) {
            State newState = getStateAfter(event);
            state = min(state, newState);
            lifecycleObserver.onStateChanged(owner, event);
            state = newState;
        }
    }
}
