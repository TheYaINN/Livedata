package livedata;

import java.util.Iterator;
import java.util.Map;

public abstract class LiveData<T> {

    static final int START_VERSION = -1;
    private int version = START_VERSION;

    private final SafeIterable<Observer<? super T>, ObserverWrapper> observers = new SafeIterable<>();

    static final Object NOT_SET = new Object();
    private volatile Object data = NOT_SET;
    private int activeCount;
    private boolean isDispatchingValue;
    private boolean isDispatchInvalidated;
    private Object mDataLock = new Object();
    private Object pendingData = NOT_SET;

    private final Runnable postValueRunnable = () -> {
        Object newValue;
        synchronized (mDataLock) {
            newValue = pendingData;
            pendingData = NOT_SET;
        }
        setValue((T) newValue);
    };

    void observe(LifecycleOwner owner, Observer<? super T> observer) {
        if (owner.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
            return;
        }

        LifecycleBoundObserver wrapper = new LifecycleBoundObserver(owner, observer);
        ObserverWrapper existing = observers.putIfAbsent(observer, wrapper);
        if (existing != null && !existing.isAttachedTo(owner)) {
            throw new IllegalArgumentException("Cannot add livedata.Observer to same LifeCycle");
        }
        if (existing != null) {
            return;
        }
        owner.getLifecycle().addObserver(wrapper);
    }

    void postValue(T value) {
        boolean postTask = true;
        synchronized (mDataLock) {
            postTask = pendingData == NOT_SET;
            pendingData = value;
        }
        if (!postTask) {
            return;
        }
        postValueRunnable.run();
    }

    void setValue(T value) {
        version++;
        data = value;
    }

    T getValue() {
        Object data = this.data;
        if (data != NOT_SET) {
            return (T) data;
        }
        return null;
    }

    int getVersion() {
        return version;
    }

    protected void onActive() {

    }

    protected void onInactive() {

    }

    public boolean hasObservers() {
        return observers.size() > 0;
    }

    public boolean hasActiveObservers() {
        return activeCount > 0;
    }

    private void considerNotify(ObserverWrapper observer) {
        if (!observer.isActive) {
            return;
        }
        // Check latest state b4 dispatch. Maybe it changed state but we didn't get the event yet.
        //
        // we still first check observer.active to keep it as the entrance for events. So even if
        // the observer moved to an active state, if we've not received that event, we better not
        // notify for a more predictable notification order.
        if (!observer.shouldBeActive()) {
            observer.activeStateChanged(false);
            return;
        }
        if (observer.lastVersion >= version) {
            return;
        }
        observer.lastVersion = version;
        observer.observer.onChanged((T) data);
    }

    void dispatchingValue(ObserverWrapper initiator) {
        if (isDispatchingValue) {
            isDispatchInvalidated = true;
            return;
        }
        isDispatchingValue = true;
        do {
            isDispatchInvalidated = false;
            if (initiator != null) {
                considerNotify(initiator);
                initiator = null;
            } else {
                for (Iterator<Map.Entry<Observer<? super T>, ObserverWrapper>> iterator =
                     observers.iteratorWithAdditions(); iterator.hasNext(); ) {
                    considerNotify(iterator.next().getValue());
                    if (isDispatchInvalidated) {
                        break;
                    }
                }
            }
        } while (isDispatchInvalidated);
        isDispatchingValue = false;
    }


    public abstract class ObserverWrapper {

        Observer<? super T> observer;
        boolean isActive;
        int lastVersion = START_VERSION;

        public ObserverWrapper(Observer<? super T> observer) {
            this.observer = observer;
        }

        abstract boolean shouldBeActive();

        boolean isAttachedTo(LifecycleOwner owner) {
            return false;
        }

        void detachObserver() {

        }

        void activeStateChanged(boolean newActive) {
            if (newActive == isActive) {
                return;
            }

            isActive = newActive;
            boolean wasInactive = LiveData.this.activeCount == 0;
            LiveData.this.activeCount += isActive ? 1 : -1;
            if (wasInactive && isActive) {
                onActive();
            }
            if (LiveData.this.activeCount == 0 && !isActive) {
                onInactive();
            }
            if (isActive) {
                dispatchingValue(this);
            }
        }

    }

    class LifecycleBoundObserver extends ObserverWrapper implements GenericLifecycleObserver {

        private LifecycleOwner owner;

        public LifecycleBoundObserver(LifecycleOwner owner, Observer<? super T> observer) {
            super(observer);
            this.owner = owner;
        }

        @Override
        boolean shouldBeActive() {
            return owner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
        }


        @Override
        public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
            if (owner.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
                removeObserver(observer);
                return;
            }
            activeStateChanged(shouldBeActive());
        }
    }

    public void removeObserver(Observer<? super T> observer) {
        ObserverWrapper removed = observers.remove(observer);
        if (removed == null) {
            return;
        }
        removed.detachObserver();
        removed.activeStateChanged(false);
    }

}
