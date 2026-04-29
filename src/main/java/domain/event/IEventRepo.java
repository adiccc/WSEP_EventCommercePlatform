package domain.event;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import domain.IRepo;
import domain.company.Company;

import java.util.List;

public interface IEventRepo  extends IRepo<Event, String> {
    List<Event> findByCompany(int companyId);
    List<Event> findByCreator(int creatorId);
    boolean tryAcquireSlot(String eventId, int capacity);
    void releaseSlot(String eventId);
    int getQueuePosition(String eventId, String token);
    int addToQueueIfAbsent(String eventId, String token);
}