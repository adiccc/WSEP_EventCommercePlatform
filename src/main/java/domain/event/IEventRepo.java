package domain.event;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import domain.IRepo;
import domain.company.Company;

import java.util.List;

public interface IEventRepo  extends IRepo<Event, Integer> {
    List<Event> findByCompany(int companyId);
    List<Event> findByCreator(int creatorId);
    List<String> getAllPurchasers();
    List<String> getAllEventPurchasers(Integer eventId);
}