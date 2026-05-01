package ua.co.tensa.modules.queue;

import java.util.Collection;
import java.util.List;

interface CommandQueueStore {
    void initialize();

    List<QueuedCommandEntry> loadAll();

    long nextId();

    void save(QueuedCommandEntry entry);

    boolean delete(long id);

    int deleteAll(Collection<Long> ids);
}
