package com.pfe.Service;


import com.pfe.ActDto.CreateActFromCrmRequest;
import com.pfe.Entity.ActIntervention;
import com.pfe.Entity.ActStatus;
import com.pfe.Entity.WorkResult;
import com.pfe.Entity.YesNo;
import com.pfe.Repository.ActRepository;
import org.springframework.stereotype.Service;
import com.pfe.ActDto.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ActService {



        private final ActRepository repo;

        public ActService(ActRepository repo) {
            this.repo = repo;
        }

        // ===== CREATE from CRM =====
        public ActIntervention createFromCrm(CreateActFromCrmRequest req) {
            repo.findByCrmCase(req.crmCase()).ifPresent(x -> {
                throw new IllegalStateException("CRM_CASE already exists: " + req.crmCase());
            });

            ActIntervention act = new ActIntervention();
            act.setCrmCase(req.crmCase());
            act.setDateCreationCrm(req.dateCreationCrm());

            act.setClient(req.client());
            act.setContactClient(req.contactClient());
            act.setCommentaire(req.commentaire());
            act.setMsisdn(req.msisdn());

            act.setLatitudeSite(req.latitudeSite());
            act.setLongitudeSite(req.longitudeSite());
            act.setLocalite(req.localite());
            act.setDelegation(req.delegation());
            act.setGouvernorat(req.gouvernorat());

            act.setOffre(req.offre());
            act.setDesPack(req.desPack());

            act.setZoneId(req.zoneId());

            // system columns
            act.setOpeningDateSurTimos(Instant.now());          // OPENING_DATE_SUR_TIMOS
            act.setStatut(ActStatus.CREATED);                  // STATUT
            act.setUpdatedAt(Instant.now());

            return repo.save(act);
        }

        // ===== ASSIGN STT =====
        public ActIntervention assignToStt(String actId, AssignSttRequest req) {
            ActIntervention act = getOrThrow(actId);

            requireStatus(act, ActStatus.CREATED);

            act.setSttCompanyId(req.sttCompanyId());
            act.setNameStt(req.nameStt());
            act.setDateAffectationStt(Instant.now());          // DATE_AFFECTATION_STT
            act.setStatut(ActStatus.ASSIGNED_TO_STT);
            act.setUpdatedAt(Instant.now());

            return repo.save(act);
        }

        // ===== RDV (STT) =====
        public ActIntervention setRdv(String actId, SetRdvRequest req) {
            ActIntervention act = getOrThrow(actId);

            requireAnyStatus(act, ActStatus.ASSIGNED_TO_STT, ActStatus.REJECTED_STT, ActStatus.REJECTED_ZM);

            if (req.abandon()) {
                if (isBlank(req.causeAbandon())) throw new IllegalArgumentException("causeAbandon required if abandon=true");
                act.setCauseAbandon(req.causeAbandon());
                act.setRepRdv(YesNo.NON);
                act.setStatut(ActStatus.CLOSED_FAILED);
                act.setUpdatedAt(Instant.now());
                return repo.save(act);
            }

            if (req.joignable()) {
                if (req.datePriseRdv() == null) throw new IllegalArgumentException("datePriseRdv required when joignable=true");
                act.setRepRdv(YesNo.OUI);
                act.setDatePriseRdv(req.datePriseRdv());       // DATE_PRISE_RDV
                act.setCmtRdv(req.cmtRdv());
                act.setCauseInjoignabilite(null);
                act.setDetail(null);
                act.setStatut(ActStatus.RDV_SET);
            } else {
                if (isBlank(req.causeInjoignabilite())) throw new IllegalArgumentException("causeInjoignabilite required when joignable=false");
                act.setRepRdv(YesNo.NON);
                act.setCauseInjoignabilite(req.causeInjoignabilite());
                act.setDetail(req.detail());
                act.setCmtRdv(req.cmtRdv());
                act.setStatut(ActStatus.RDV_FAILED);
            }

            act.setUpdatedAt(Instant.now());
            return repo.save(act);
        }

        // ===== ASSIGN TECH (STT) =====
        public ActIntervention assignTechnician(String actId, AssignTechnicianRequest req) {
            ActIntervention act = getOrThrow(actId);

            requireAnyStatus(act, ActStatus.ASSIGNED_TO_STT, ActStatus.RDV_SET);

            if (isBlank(req.technicianUserId())) throw new IllegalArgumentException("technicianUserId required");

            act.setTechnicianUserId(req.technicianUserId());
            // status stays ASSIGNED_TO_STT or RDV_SET; you can also add ASSIGNED_TO_TECH if you want
            act.setUpdatedAt(Instant.now());

            return repo.save(act);
        }

        // ===== FIRST EVIDENCE (called by Photo pipeline later) =====
        public ActIntervention markInExecution(String actId) {
            ActIntervention act = getOrThrow(actId);

            // allow from RDV_SET or ASSIGNED_TO_STT if you want flexibility
            requireAnyStatus(act, ActStatus.RDV_SET, ActStatus.ASSIGNED_TO_STT);

            act.setStatut(ActStatus.IN_EXECUTION);
            act.setUpdatedAt(Instant.now());

            return repo.save(act);
        }

        // ===== TECH SUBMIT (finish evidence) =====
        public ActIntervention submit(String actId) {
            ActIntervention act = getOrThrow(actId);

            requireAnyStatus(act, ActStatus.IN_EXECUTION, ActStatus.RDV_SET);

            // Here later: verify mandatory photos + GPS/time compliance
            act.setDateFinTrv(Instant.now());                 // DATE_FIN_TRV
            act.setStatut(ActStatus.SUBMITTED);
            act.setUpdatedAt(Instant.now());

            return repo.save(act);
        }

        // ===== STT PREVALIDATE =====
        public ActIntervention sttPrevalidate(String actId, SttPrevalidateRequest req) {
            ActIntervention act = getOrThrow(actId);

            requireStatus(act, ActStatus.SUBMITTED);

            if (req.approve()) {
                act.setRepTravauxStt(WorkResult.OK);          // REP_TRAVAUX_STT
                act.setCmtTrvStt(req.comment());
                act.setStatut(ActStatus.PREVALIDATED_STT);
            } else {
                act.setRepTravauxStt(WorkResult.KO);
                act.setCmtTrvStt(req.comment());
                act.setStatut(ActStatus.REJECTED_STT);
            }

            act.setUpdatedAt(Instant.now());
            return repo.save(act);
        }

        // ===== ZM VALIDATE =====
        @Transactional
        public ActIntervention zmValidate(String actId, ZmValidateRequest req) {
            ActIntervention act = getOrThrow(actId);

            requireStatus(act, ActStatus.PREVALIDATED_STT);

            if (req.approve()) {
                act.setRepZm(YesNo.OUI);
                act.setCmtZm(req.comment());
                act.setStatut(ActStatus.CLOSED_SUCCESS);
                // Later: Stock deduction happens here after validation
            } else {
                act.setRepZm(YesNo.NON);
                act.setCmtZm(req.comment());
                act.setStatut(ActStatus.REJECTED_ZM);
            }

            act.setUpdatedAt(Instant.now());
            return repo.save(act);
        }

        // ===== METRICS update (ONLY pipeline/admin) =====
        public ActIntervention updateMetrics(String actId, UpdateMetricsRequest req) {
            ActIntervention act = getOrThrow(actId);

            // allow any non-closed status; or restrict to IN_EXECUTION/SUBMITTED
            if (act.getStatut() == ActStatus.CLOSED_SUCCESS || act.getStatut() == ActStatus.CLOSED_FAILED) {
                throw new IllegalStateException("Cannot update metrics on closed intervention");
            }

            act.setMetrageCable(req.metrageCable());
            act.setNombreDePv(req.nombreDePv());
            act.setDebitDl(req.debitDl());
            act.setDebitUl(req.debitUl());
            act.setRsrp(req.rsrp());
            act.setRsrq(req.rsrq());
            act.setSinr(req.sinr());
            act.setSiteSecteur(req.siteSecteur());

            act.setUpdatedAt(Instant.now());
            return repo.save(act);
        }

        // ===== helpers =====
        private ActIntervention getOrThrow(String id) {
            return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("ACT not found: " + id));
        }

        private void requireStatus(ActIntervention act, ActStatus expected) {
            if (act.getStatut() != expected) {
                throw new IllegalStateException("Invalid status. Expected " + expected + " but got " + act.getStatut());
            }
        }

        private void requireAnyStatus(ActIntervention act, ActStatus... allowed) {
            for (ActStatus s : allowed) {
                if (act.getStatut() == s) return;
            }
            throw new IllegalStateException("Invalid status: " + act.getStatut());
        }

        private boolean isBlank(String s) {
            return s == null || s.trim().isEmpty();
        }
    }

