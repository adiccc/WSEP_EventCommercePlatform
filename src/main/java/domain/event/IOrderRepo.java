package domain.event;

import domain.IRepo;

public interface IOrderRepo extends IRepo<Order, Integer> {
    int getTicketsBoughtByUserForEvent(int userId, int eventId);
}
