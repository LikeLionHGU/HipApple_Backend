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

    @Column(nullable = false)
    private String farmLocation;

    @Column(nullable = false)
    private String variety;

    @Column(nullable = false)
    private int farmSize;

    @Column(nullable = false)
    private String farmSizeUnit;

    @Column(nullable = false)
    private String shipmentType;

    protected UserProfile() {
    }

    public UserProfile(ProfileRequest request) {
        update(request);
    }

    public Long getId() {
        return id;
    }

    public String getVariety() {
        return variety;
    }

    public void update(ProfileRequest request) {
        this.farmLocation = request.farmLocation();
        this.variety = request.variety();
        this.farmSize = request.farmSize();
        this.farmSizeUnit = request.farmSizeUnit();
        this.shipmentType = request.shipmentType();
    }
}
