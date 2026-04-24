package domain.dataType;

import java.time.LocalDateTime;

public class EventSearchFilter {
    public String keyword;
    public Double minPrice;
    public Double maxPrice;
    public LocalDateTime startDate;
    public LocalDateTime endDate;
    public CategoryEvent category;
    public GeographicalArea location;

    public EventSearchFilter(String keyword, GeographicalArea geographicalArea, CategoryEvent categoryEvent, LocalDateTime startDate, LocalDateTime endDate, Double minPrice, Double maxPrice) {
        this.keyword = keyword;
        this.location = geographicalArea;
        this.category = categoryEvent;
        this.startDate = startDate;
        this.endDate = endDate;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
    }
    public String getKeyword() {
        return keyword;
    }

    public Double getMinPrice() {
        return minPrice;
    }

    public Double getMaxPrice() {
        return maxPrice;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public CategoryEvent getCategory() {
        return category;
    }

    public GeographicalArea getLocation() {
        return location;
    }
}
