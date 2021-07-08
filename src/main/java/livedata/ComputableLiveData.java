package livedata;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ComputableLiveData<T> {

    final LiveData<T> liveData;
    final Executor executor;
    final AtomicBoolean isComputing = new AtomicBoolean(false);
    final AtomicBoolean isInvalid = new AtomicBoolean(true);

    private Runnable refreshable = new Runnable() {
        @Override
        public void run() {
            boolean computed;
            do {
                computed = false;
                if (isComputing.compareAndSet(false, true)) {
                    try {
                        T value = null;
                        while (isInvalid.compareAndSet(true, false)) {
                            computed = true;
                            value = compute();
                        }
                        if (computed) {
                            liveData.postValue(value);
                        }
                    } finally {
                        isComputing.set(false);
                    }
                }
            } while (computed && isInvalid.get());
        }
    };

    public ComputableLiveData(Executor executor) {
        this.executor = executor;
        liveData = new LiveData<T>() {

            @Override
            protected void onActive() {
                executor.execute(refreshable);
            }
        };
    }

    public LiveData<T> getLiveData() {
        return liveData;
    }

    protected abstract T compute();

}
