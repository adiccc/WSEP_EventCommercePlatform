package domain;

import java.util.List;

public interface IRepo<T, ID> {
    T findById(ID id);
    List<T> getAll();
    void delete(ID id);
    void store(T entity);
    void update(T entity);
}
