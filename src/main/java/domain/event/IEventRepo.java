package domain.event;

import DTO.ElementPositionDTO;
import DTO.SeatingZoneDTO;
import DTO.StandingZoneDTO;
import domain.IRepo;
import domain.company.Company;

import java.util.List;

public interface IEventRepo  extends IRepo<Event, String> {
    List<Event> findByCompany(int companyId);

}