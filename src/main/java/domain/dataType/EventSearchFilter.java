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

    public EventSearchFilter() {
        this.keyword = null;
        this.location = null;
        this.category = null;
        this.startDate = null;
        this.endDate = null;
        this.minPrice = null;
        this.maxPrice = null;
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

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public void setMinPrice(Double minPrice) {
        this.minPrice = minPrice;
    }

    public void setMaxPrice(Double maxPrice) {
        this.maxPrice = maxPrice;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public void setCategory(CategoryEvent category) {
        this.category = category;
    }

    public void setLocation(GeographicalArea location) {
        this.location = location;
    }
}
