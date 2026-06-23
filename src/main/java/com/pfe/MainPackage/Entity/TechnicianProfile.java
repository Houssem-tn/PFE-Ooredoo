package com.pfe.MainPackage.Entity;

public class TechnicianProfile {
    private String matricule;   // unique internal tech id
    private String skillSet;    // ex: "Fiber, Router"
    private String region;      // ex: "Ariana"
    private boolean available = true;

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public String getSkillSet() { return skillSet; }
    public void setSkillSet(String skillSet) { this.skillSet = skillSet; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}
