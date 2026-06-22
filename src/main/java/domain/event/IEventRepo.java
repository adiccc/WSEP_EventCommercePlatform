package domain.event;

import domain.IRepo;

import java.util.List;

public interface IEventRepo  extends IRepo<Event, Integer> {
    List<Event> findByCompany(int companyId);
    List<Event> findByCreator(int creatorId);
    List<String> getAllPurchasers();
    List<String> getAllEventPurchasers(Integer eventId);
}