package com.lionapple.user;

import com.lionapple.user.dto.ProfileRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private String variety;

    @Column(nullable = false) // 또는 false
    private String farmName;

    @Column(nullable = false)
    private int farmSize;

    @Column(nullable = false)
    private String farmSizeUnit;

    @Column(nullable = false)
    private String shipmentType;

    @Column
    private String farmLocation;

    protected UserProfile() {
    }

    public UserProfile(Long userId, ProfileRequest request) {
        this.userId = userId;
        update(request);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getVariety() {
        return variety;
    }

    public String getFarmName() {return farmName;}

    public String getFarmLocation() {return farmLocation;}

    public void update(ProfileRequest request) {
        this.variety = request.variety();
        this.farmSize = request.farmSize();
        this.farmSizeUnit = request.farmSizeUnit();
        this.shipmentType = request.shipmentType();
        this.farmName = request.farmName();
        this.farmLocation = request.farmLocation();
    }
}
