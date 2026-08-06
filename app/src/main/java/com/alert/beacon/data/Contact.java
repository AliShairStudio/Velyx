package com.alert.beacon.data;

public class Contact {
    private String id;
    private String name;
    private String phoneNumber;
    private String relation;

    // Required empty constructor for Firebase
    public Contact() {}

    public Contact(String id, String name, String phoneNumber, String relation) {
        this.id = id;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.relation = relation;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getRelation() { return relation; }
    public void setRelation(String relation) { this.relation = relation; }
}
