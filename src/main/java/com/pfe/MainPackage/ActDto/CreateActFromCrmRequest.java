package com.pfe.MainPackage.ActDto;

import java.time.Instant;

public record CreateActFromCrmRequest(
        String crmCase,
        Instant dateCreationCrm,
        String client,
        String contactClient,
        String commentaire,
        String msisdn,
        Double latitudeSite,
        Double longitudeSite,
        String localite,
        String delegation,
        String gouvernorat,
        String offre,
        String desPack,
        String zoneId
) { }
