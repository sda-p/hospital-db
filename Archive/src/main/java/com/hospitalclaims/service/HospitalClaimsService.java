package com.hospitalclaims.service;

import com.hospitalclaims.model.Claim;
import com.hospitalclaims.model.Doctor;
import com.hospitalclaims.model.Drug;
import com.hospitalclaims.model.Insurance;
import com.hospitalclaims.model.Patient;
import com.hospitalclaims.model.Prescription;
import com.hospitalclaims.model.Visit;
import com.hospitalclaims.repository.ClaimRepository;
import com.hospitalclaims.repository.DoctorRepository;
import com.hospitalclaims.repository.DrugRepository;
import com.hospitalclaims.repository.InsuranceRepository;
import com.hospitalclaims.repository.PatientRepository;
import com.hospitalclaims.repository.PrescriptionRepository;
import com.hospitalclaims.repository.VisitKey;
import com.hospitalclaims.repository.VisitRepository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Coordinates validation, persistence, and workflow rules for hospital claims. */
public class HospitalClaimsService {
    private final ClaimRepository claimRepository;
    private final InsuranceRepository insuranceRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final DrugRepository drugRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final VisitRepository visitRepository;

    public HospitalClaimsService(ClaimRepository claimRepository,
                                 InsuranceRepository insuranceRepository,
                                 PatientRepository patientRepository,
                                 DoctorRepository doctorRepository,
                                 DrugRepository drugRepository,
                                 PrescriptionRepository prescriptionRepository,
                                 VisitRepository visitRepository) {
        this.claimRepository = claimRepository;
        this.insuranceRepository = insuranceRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.drugRepository = drugRepository;
        this.prescriptionRepository = prescriptionRepository;
        this.visitRepository = visitRepository;
    }

    /** Validates and stores a new insurance provider. */
    public void registerInsurance(Insurance insurance) throws SQLException {
        validateInsurance(insurance);
        insuranceRepository.save(insurance);
    }

    /** Validates and updates an existing insurance provider. */
    public void updateInsurance(Insurance insurance) throws SQLException {
        validateInsurance(insurance);
        ensureInsuranceExists(insurance.getInsuranceId());
        insuranceRepository.update(insurance);
    }

    /** Validates and stores a new patient record. */
    public void registerPatient(Patient patient) throws SQLException {
        validatePatient(patient);
        ensureInsuranceExists(patient.getInsuranceId());
        ensureDoctorExists(patient.getPrimaryCareDoctorId(), "primaryCareDoctorId");
        patientRepository.save(patient);
    }

    /** Validates and stores a new doctor record. */
    public void registerDoctor(Doctor doctor) throws SQLException {
        validateDoctor(doctor);
        doctorRepository.save(doctor);
    }

    /** Validates and updates an existing patient record. */
    public void updatePatient(Patient patient) throws SQLException {
        validatePatient(patient);
        ensurePatientExists(patient.getPatientId());
        ensureInsuranceExists(patient.getInsuranceId());
        ensureDoctorExists(patient.getPrimaryCareDoctorId(), "primaryCareDoctorId");
        patientRepository.update(patient);
    }

    /** Validates and updates an existing doctor record. */
    public void updateDoctor(Doctor doctor) throws SQLException {
        validateDoctor(doctor);
        ensureDoctorExists(doctor.getDoctorId(), "doctorId");
        doctorRepository.update(doctor);
    }

    /** Validates and stores a new drug catalogue entry. */
    public void registerDrug(Drug drug) throws SQLException {
        validateDrug(drug);
        drugRepository.save(drug);
    }

    /** Validates and updates an existing drug catalogue entry. */
    public void updateDrug(Drug drug) throws SQLException {
        validateDrug(drug);
        ensureDrugExists(drug.getDrugId());
        drugRepository.update(drug);
    }

    /** Records a prescription after referential and overlap checks pass. */
    public void recordPrescription(Prescription prescription) throws SQLException {
        validatePrescription(prescription);
        ensureDrugExists(prescription.getDrugId());
        ensureDoctorExists(prescription.getDoctorId(), "doctorId");
        ensurePatientExists(prescription.getPatientId());
        rejectDuplicateActivePrescription(prescription);
        prescriptionRepository.save(prescription);
    }

    /** Updates a prescription while preserving the same overlap safeguards as creation. */
    public void updatePrescription(Prescription prescription) throws SQLException {
        validatePrescription(prescription);
        ensurePrescriptionExists(prescription.getPrescriptionId());
        ensureDrugExists(prescription.getDrugId());
        ensureDoctorExists(prescription.getDoctorId(), "doctorId");
        ensurePatientExists(prescription.getPatientId());
        rejectDuplicateActivePrescription(prescription);
        prescriptionRepository.update(prescription);
    }

    /** Records a patient visit after validating both linked parties exist. */
    public void recordVisit(Visit visit) throws SQLException {
        validateVisit(visit);
        ensurePatientExists(visit.getPatientId());
        ensureDoctorExists(visit.getDoctorId(), "doctorId");
        visitRepository.save(visit);
    }

    /** Updates a visit addressed by its original composite key. */
    public void updateVisit(String originalPatientId,
                            String originalDoctorId,
                            LocalDate originalDateOfVisit,
                            Visit visit) throws SQLException {
        ValidationUtils.requireDate(originalDateOfVisit, "originalDateOfVisit");
        VisitKey originalKey = new VisitKey(
                ValidationUtils.requireNonBlank(originalPatientId, "originalPatientId"),
                ValidationUtils.requireNonBlank(originalDoctorId, "originalDoctorId"),
                originalDateOfVisit
        );
        validateVisit(visit);
        ensureVisitExists(originalKey);
        ensurePatientExists(visit.getPatientId());
        ensureDoctorExists(visit.getDoctorId(), "doctorId");
        visitRepository.update(originalKey, visit);
    }

    /** Creates a draft claim and backfills the patient and insurer from the prescription. */
    public Claim createClaim(String claimId, String prescriptionId, String notes) throws SQLException {
        Claim claim = new Claim(
                ValidationUtils.requireNonBlank(claimId, "claimId"),
                ValidationUtils.requireNonBlank(prescriptionId, "prescriptionId"),
                null,
                null,
                ClaimStatus.DRAFT.name(),
                LocalDate.now(),
                null,
                null,
                null,
                ValidationUtils.optionalTrimmed(notes)
        );
        populateClaimReferences(claim);
        validateClaim(claim);
        if (claimRepository.findByPrescriptionId(claim.getPrescriptionId()).isPresent()) {
            throw new ValidationException("prescriptionId " + claim.getPrescriptionId() + " already has a claim.");
        }
        claimRepository.save(claim);
        return claim;
    }

    /** Moves a draft claim into the submitted state. */
    public Claim submitClaim(String claimId) throws SQLException {
        Claim claim = requireClaim(claimId);
        ensureClaimStatus(claim, ClaimStatus.DRAFT);
        claim.setStatus(ClaimStatus.SUBMITTED.name());
        claim.setSubmittedDate(LocalDate.now());
        claimRepository.update(claim);
        return claim;
    }

    /** Assigns a reviewer and moves a submitted claim into review. */
    public Claim markClaimUnderReview(String claimId, String reviewer) throws SQLException {
        Claim claim = requireClaim(claimId);
        ensureClaimStatus(claim, ClaimStatus.SUBMITTED);
        claim.setStatus(ClaimStatus.UNDER_REVIEW.name());
        claim.setReviewedBy(ValidationUtils.requireNonBlank(reviewer, "reviewedBy"));
        claimRepository.update(claim);
        return claim;
    }

    /** Approves a submitted or in-review claim. */
    public Claim approveClaim(String claimId, String reviewer, String notes) throws SQLException {
        Claim claim = requireClaim(claimId);
        ensureClaimStatus(claim, ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW);
        claim.setStatus(ClaimStatus.APPROVED.name());
        claim.setReviewedBy(ValidationUtils.requireNonBlank(reviewer, "reviewedBy"));
        claim.setDecisionDate(LocalDate.now());
        claim.setDecisionNotes(ValidationUtils.optionalTrimmed(notes));
        claimRepository.update(claim);
        return claim;
    }

    /** Rejects a submitted or in-review claim with a mandatory rationale. */
    public Claim rejectClaim(String claimId, String reviewer, String notes) throws SQLException {
        Claim claim = requireClaim(claimId);
        ensureClaimStatus(claim, ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW);
        claim.setStatus(ClaimStatus.REJECTED.name());
        claim.setReviewedBy(ValidationUtils.requireNonBlank(reviewer, "reviewedBy"));
        claim.setDecisionDate(LocalDate.now());
        claim.setDecisionNotes(ValidationUtils.requireNonBlank(notes, "decisionNotes"));
        claimRepository.update(claim);
        return claim;
    }

    /** Deletes a claim by id. */
    public void deleteClaim(String claimId) throws SQLException {
        ensureClaimExists(claimId);
        claimRepository.deleteById(claimId);
    }

    /** Deletes an insurer once all patient and claim references are gone. */
    public void deleteInsurance(String insuranceId) throws SQLException {
        String normalizedInsuranceId = ValidationUtils.requireNonBlank(insuranceId, "insuranceId");
        ensureInsuranceExists(normalizedInsuranceId);
        if (getAllPatients().stream().anyMatch(patient -> normalizedInsuranceId.equals(patient.getInsuranceId()))) {
            throw new ValidationException("insuranceId " + normalizedInsuranceId + " is still referenced by patient records.");
        }
        if (claimRepository.findAll().stream().anyMatch(claim -> normalizedInsuranceId.equals(claim.getInsuranceId()))) {
            throw new ValidationException("insuranceId " + normalizedInsuranceId + " is still referenced by claims.");
        }
        insuranceRepository.deleteById(normalizedInsuranceId);
    }

    /** Deletes a patient once visits, prescriptions, and claims no longer reference them. */
    public void deletePatient(String patientId) throws SQLException {
        String normalizedPatientId = ValidationUtils.requireNonBlank(patientId, "patientId");
        ensurePatientExists(normalizedPatientId);
        if (!visitRepository.findByPatientId(normalizedPatientId).isEmpty()) {
            throw new ValidationException("patientId " + normalizedPatientId + " still has visits on file.");
        }
        if (!prescriptionRepository.findByPatientId(normalizedPatientId).isEmpty()) {
            throw new ValidationException("patientId " + normalizedPatientId + " still has prescriptions on file.");
        }
        if (!claimRepository.findByPatientId(normalizedPatientId).isEmpty()) {
            throw new ValidationException("patientId " + normalizedPatientId + " still has claims on file.");
        }
        patientRepository.deleteById(normalizedPatientId);
    }

    /** Deletes a doctor once no patient, visit, or prescription still depends on them. */
    public void deleteDoctor(String doctorId) throws SQLException {
        String normalizedDoctorId = ValidationUtils.requireNonBlank(doctorId, "doctorId");
        ensureDoctorExists(normalizedDoctorId, "doctorId");
        if (getAllPatients().stream().anyMatch(patient -> normalizedDoctorId.equals(patient.getPrimaryCareDoctorId()))) {
            throw new ValidationException("doctorId " + normalizedDoctorId + " is still assigned as a primary care doctor.");
        }
        if (getAllVisits().stream().anyMatch(visit -> normalizedDoctorId.equals(visit.getDoctorId()))) {
            throw new ValidationException("doctorId " + normalizedDoctorId + " still has visits on file.");
        }
        if (getAllPrescriptions().stream().anyMatch(prescription -> normalizedDoctorId.equals(prescription.getDoctorId()))) {
            throw new ValidationException("doctorId " + normalizedDoctorId + " still has prescriptions on file.");
        }
        doctorRepository.deleteById(normalizedDoctorId);
    }

    /** Deletes a drug once no prescription still references it. */
    public void deleteDrug(String drugId) throws SQLException {
        String normalizedDrugId = ValidationUtils.requireNonBlank(drugId, "drugId");
        ensureDrugExists(normalizedDrugId);
        if (getAllPrescriptions().stream().anyMatch(prescription -> normalizedDrugId.equals(prescription.getDrugId()))) {
            throw new ValidationException("drugId " + normalizedDrugId + " still has prescriptions on file.");
        }
        drugRepository.deleteById(normalizedDrugId);
    }

    /** Deletes a prescription once no claim still references it. */
    public void deletePrescription(String prescriptionId) throws SQLException {
        String normalizedPrescriptionId = ValidationUtils.requireNonBlank(prescriptionId, "prescriptionId");
        ensurePrescriptionExists(normalizedPrescriptionId);
        if (claimRepository.findByPrescriptionId(normalizedPrescriptionId).isPresent()) {
            throw new ValidationException("prescriptionId " + normalizedPrescriptionId + " still has a claim on file.");
        }
        prescriptionRepository.deleteById(normalizedPrescriptionId);
    }

    /** Deletes a visit addressed by its composite key. */
    public void deleteVisit(String patientId, String doctorId, LocalDate dateOfVisit) throws SQLException {
        VisitKey visitKey = new VisitKey(
                ValidationUtils.requireNonBlank(patientId, "patientId"),
                ValidationUtils.requireNonBlank(doctorId, "doctorId"),
                dateOfVisit
        );
        ensureVisitExists(visitKey);
        visitRepository.deleteById(visitKey);
    }

    /** Looks up one insurance provider by id. */
    public Optional<Insurance> findInsuranceById(String insuranceId) throws SQLException {
        return insuranceRepository.findById(ValidationUtils.requireNonBlank(insuranceId, "insuranceId"));
    }

    /** Looks up one patient by id. */
    public Optional<Patient> findPatientById(String patientId) throws SQLException {
        return patientRepository.findById(ValidationUtils.requireNonBlank(patientId, "patientId"));
    }

    /** Looks up one doctor by id. */
    public Optional<Doctor> findDoctorById(String doctorId) throws SQLException {
        return doctorRepository.findById(ValidationUtils.requireNonBlank(doctorId, "doctorId"));
    }

    /** Looks up one drug by id. */
    public Optional<Drug> findDrugById(String drugId) throws SQLException {
        return drugRepository.findById(ValidationUtils.requireNonBlank(drugId, "drugId"));
    }

    /** Looks up one prescription by id. */
    public Optional<Prescription> findPrescriptionById(String prescriptionId) throws SQLException {
        return prescriptionRepository.findById(ValidationUtils.requireNonBlank(prescriptionId, "prescriptionId"));
    }

    /** Looks up one claim by id. */
    public Optional<Claim> findClaimById(String claimId) throws SQLException {
        return claimRepository.findById(ValidationUtils.requireNonBlank(claimId, "claimId"));
    }

    /** Looks up one visit by its composite key. */
    public Optional<Visit> findVisit(String patientId, String doctorId, LocalDate dateOfVisit) throws SQLException {
        return visitRepository.findById(new VisitKey(
                ValidationUtils.requireNonBlank(patientId, "patientId"),
                ValidationUtils.requireNonBlank(doctorId, "doctorId"),
                dateOfVisit
        ));
    }

    /** Returns every insurance provider. */
    public List<Insurance> getAllInsuranceProviders() throws SQLException {
        return insuranceRepository.findAll();
    }

    /** Returns every patient. */
    public List<Patient> getAllPatients() throws SQLException {
        return patientRepository.findAll();
    }

    /** Returns every doctor. */
    public List<Doctor> getAllDoctors() throws SQLException {
        return doctorRepository.findAll();
    }

    /** Returns every drug. */
    public List<Drug> getAllDrugs() throws SQLException {
        return drugRepository.findAll();
    }

    /** Returns every prescription. */
    public List<Prescription> getAllPrescriptions() throws SQLException {
        return prescriptionRepository.findAll();
    }

    /** Returns every claim. */
    public List<Claim> getAllClaims() throws SQLException {
        return claimRepository.findAll();
    }

    /** Returns every visit. */
    public List<Visit> getAllVisits() throws SQLException {
        return visitRepository.findAll();
    }

    /** Searches patients by surname fragment. */
    public List<Patient> findPatientsBySurname(String surname) throws SQLException {
        return patientRepository.findBySurname(ValidationUtils.requireNonBlank(surname, "surname"));
    }

    /** Searches doctors by specialization fragment. */
    public List<Doctor> findDoctorsBySpecialization(String specialization) throws SQLException {
        return doctorRepository.findBySpecialization(ValidationUtils.requireNonBlank(specialization, "specialization"));
    }

    /** Searches drugs by name fragment. */
    public List<Drug> findDrugsByName(String drugName) throws SQLException {
        return drugRepository.findByName(ValidationUtils.requireNonBlank(drugName, "drugName"));
    }

    /** Returns a patient's visit history. */
    public List<Visit> getVisitHistoryForPatient(String patientId) throws SQLException {
        return visitRepository.findByPatientId(ValidationUtils.requireNonBlank(patientId, "patientId"));
    }

    /** Returns a patient's prescription history. */
    public List<Prescription> getPrescriptionHistoryForPatient(String patientId) throws SQLException {
        return prescriptionRepository.findByPatientId(ValidationUtils.requireNonBlank(patientId, "patientId"));
    }

    /** Builds a patient history report with linked insurance, doctor, visits, and prescriptions. */
    public Optional<PatientHistoryReport> getPatientHistoryReport(String patientId) throws SQLException {
        Optional<Patient> patient = findPatientById(patientId);
        if (patient.isEmpty()) {
            return Optional.empty();
        }

        Insurance insurance = null;
        if (patient.get().getInsuranceId() != null && !patient.get().getInsuranceId().isBlank()) {
            insurance = insuranceRepository.findById(patient.get().getInsuranceId()).orElse(null);
        }

        Doctor primaryCareDoctor = null;
        if (patient.get().getPrimaryCareDoctorId() != null && !patient.get().getPrimaryCareDoctorId().isBlank()) {
            primaryCareDoctor = doctorRepository.findById(patient.get().getPrimaryCareDoctorId()).orElse(null);
        }

        // Visit and prescription sections are loaded separately so the report remains usable
        // even when optional linked records such as insurer or doctor are missing.
        List<Visit> visits = visitRepository.findByPatientId(patient.get().getPatientId());
        List<PrescriptionReview> prescriptions = buildPrescriptionReviews(
                prescriptionRepository.findByPatientId(patient.get().getPatientId())
        );

        return Optional.of(new PatientHistoryReport(
                patient.get(),
                insurance,
                primaryCareDoctor,
                visits,
                prescriptions
        ));
    }

    /** Returns review metadata for every prescription. */
    public List<PrescriptionReview> getPrescriptionReviews() throws SQLException {
        return buildPrescriptionReviews(prescriptionRepository.findAll());
    }

    /** Returns review metadata for one patient's prescriptions. */
    public List<PrescriptionReview> getPrescriptionReviewsForPatient(String patientId) throws SQLException {
        return buildPrescriptionReviews(getPrescriptionHistoryForPatient(patientId));
    }

    /** Returns every claim paired with its prescription review context. */
    public List<ClaimView> getClaimViews() throws SQLException {
        return buildClaimViews(claimRepository.findAll());
    }

    /** Returns one patient's claims paired with prescription review context. */
    public List<ClaimView> getClaimViewsForPatient(String patientId) throws SQLException {
        return buildClaimViews(claimRepository.findByPatientId(ValidationUtils.requireNonBlank(patientId, "patientId")));
    }

    private void validateInsurance(Insurance insurance) {
        if (insurance == null) {
            throw new ValidationException("insurance must not be null.");
        }
        insurance.setInsuranceId(ValidationUtils.requireNonBlank(insurance.getInsuranceId(), "insuranceId"));
        insurance.setCompany(ValidationUtils.requireNonBlank(insurance.getCompany(), "company"));
        insurance.setAddress(ValidationUtils.optionalTrimmed(insurance.getAddress()));
        insurance.setPhone(ValidationUtils.optionalTrimmed(insurance.getPhone()));
    }

    private void validateClaim(Claim claim) {
        if (claim == null) {
            throw new ValidationException("claim must not be null.");
        }
        claim.setClaimId(ValidationUtils.requireNonBlank(claim.getClaimId(), "claimId"));
        claim.setPrescriptionId(ValidationUtils.requireNonBlank(claim.getPrescriptionId(), "prescriptionId"));
        claim.setPatientId(ValidationUtils.requireNonBlank(claim.getPatientId(), "patientId"));
        claim.setInsuranceId(ValidationUtils.requireNonBlank(claim.getInsuranceId(), "insuranceId"));
        claim.setStatus(parseClaimStatus(claim.getStatus()).name());
        ValidationUtils.requireDate(claim.getCreatedDate(), "createdDate");
        claim.setReviewedBy(ValidationUtils.optionalTrimmed(claim.getReviewedBy()));
        claim.setDecisionNotes(ValidationUtils.optionalTrimmed(claim.getDecisionNotes()));
    }

    private void validatePatient(Patient patient) {
        if (patient == null) {
            throw new ValidationException("patient must not be null.");
        }
        patient.setPatientId(ValidationUtils.requireNonBlank(patient.getPatientId(), "patientId"));
        patient.setFirstName(ValidationUtils.requireNonBlank(patient.getFirstName(), "firstName"));
        patient.setSurname(ValidationUtils.requireNonBlank(patient.getSurname(), "surname"));
        patient.setPostcode(ValidationUtils.optionalTrimmed(patient.getPostcode()));
        patient.setAddress(ValidationUtils.optionalTrimmed(patient.getAddress()));
        patient.setPhone(ValidationUtils.optionalTrimmed(patient.getPhone()));
        patient.setEmail(ValidationUtils.requireNonBlank(patient.getEmail(), "email"));
        ValidationUtils.requireEmail(patient.getEmail(), "email");
        patient.setInsuranceId(ValidationUtils.optionalTrimmed(patient.getInsuranceId()));
        patient.setPrimaryCareDoctorId(ValidationUtils.optionalTrimmed(patient.getPrimaryCareDoctorId()));
    }

    private void validateDoctor(Doctor doctor) {
        if (doctor == null) {
            throw new ValidationException("doctor must not be null.");
        }
        doctor.setDoctorId(ValidationUtils.requireNonBlank(doctor.getDoctorId(), "doctorId"));
        doctor.setFirstName(ValidationUtils.requireNonBlank(doctor.getFirstName(), "firstName"));
        doctor.setSurname(ValidationUtils.requireNonBlank(doctor.getSurname(), "surname"));
        doctor.setAddress(ValidationUtils.optionalTrimmed(doctor.getAddress()));
        doctor.setPhone(ValidationUtils.optionalTrimmed(doctor.getPhone()));
        doctor.setEmail(ValidationUtils.requireNonBlank(doctor.getEmail(), "email"));
        ValidationUtils.requireEmail(doctor.getEmail(), "email");
        doctor.setSpecialization(ValidationUtils.optionalTrimmed(doctor.getSpecialization()));
        doctor.setHospital(ValidationUtils.optionalTrimmed(doctor.getHospital()));
    }

    private void validateDrug(Drug drug) {
        if (drug == null) {
            throw new ValidationException("drug must not be null.");
        }
        drug.setDrugId(ValidationUtils.requireNonBlank(drug.getDrugId(), "drugId"));
        drug.setDrugName(ValidationUtils.requireNonBlank(drug.getDrugName(), "drugName"));
        drug.setSideEffects(ValidationUtils.optionalTrimmed(drug.getSideEffects()));
        drug.setPurpose(ValidationUtils.optionalTrimmed(drug.getPurpose()));
    }

    private void validatePrescription(Prescription prescription) {
        if (prescription == null) {
            throw new ValidationException("prescription must not be null.");
        }
        prescription.setPrescriptionId(ValidationUtils.requireNonBlank(prescription.getPrescriptionId(), "prescriptionId"));
        ValidationUtils.requireDate(prescription.getDatePrescribed(), "datePrescribed");
        prescription.setDosage(ValidationUtils.requireNonBlank(prescription.getDosage(), "dosage"));
        prescription.setDuration(ValidationUtils.requireNonBlank(prescription.getDuration(), "duration"));
        ValidationUtils.requirePositiveInteger(prescription.getDuration(), "duration");
        prescription.setComment(ValidationUtils.optionalTrimmed(prescription.getComment()));
        prescription.setDrugId(ValidationUtils.requireNonBlank(prescription.getDrugId(), "drugId"));
        prescription.setDoctorId(ValidationUtils.requireNonBlank(prescription.getDoctorId(), "doctorId"));
        prescription.setPatientId(ValidationUtils.requireNonBlank(prescription.getPatientId(), "patientId"));
    }

    private void validateVisit(Visit visit) {
        if (visit == null) {
            throw new ValidationException("visit must not be null.");
        }
        visit.setPatientId(ValidationUtils.requireNonBlank(visit.getPatientId(), "patientId"));
        visit.setDoctorId(ValidationUtils.requireNonBlank(visit.getDoctorId(), "doctorId"));
        ValidationUtils.requireDate(visit.getDateOfVisit(), "dateOfVisit");
        visit.setSymptoms(ValidationUtils.optionalTrimmed(visit.getSymptoms()));
        visit.setDiagnosisId(ValidationUtils.optionalTrimmed(visit.getDiagnosisId()));
    }

    private void rejectDuplicateActivePrescription(Prescription prescription) throws SQLException {
        int durationDays = ValidationUtils.requirePositiveInteger(prescription.getDuration(), "duration");
        LocalDate newPrescriptionEndDate = prescription.getDatePrescribed().plusDays(durationDays - 1L);

        for (Prescription existing : prescriptionRepository.findByPatientId(prescription.getPatientId())) {
            if (prescription.getPrescriptionId().equals(existing.getPrescriptionId())) {
                continue;
            }
            if (!prescription.getDrugId().equals(existing.getDrugId())) {
                continue;
            }

            int existingDurationDays = ValidationUtils.requirePositiveInteger(existing.getDuration(), "duration");
            // Overlap is inclusive because both prescriptions are active on their start and end dates.
            LocalDate existingEndDate = existing.getDatePrescribed().plusDays(existingDurationDays - 1L);
            boolean overlaps = !existing.getDatePrescribed().isAfter(newPrescriptionEndDate)
                    && !existingEndDate.isBefore(prescription.getDatePrescribed());

            if (overlaps) {
                throw new ValidationException(
                        "patient already has an active prescription for drugId " + prescription.getDrugId() + "."
                );
            }
        }
    }

    private void ensureInsuranceExists(String insuranceId) throws SQLException {
        if (insuranceId == null || insuranceId.isBlank()) {
            return;
        }
        if (insuranceRepository.findById(insuranceId).isEmpty()) {
            throw new ValidationException("insuranceId " + insuranceId + " does not exist.");
        }
    }

    private void ensurePatientExists(String patientId) throws SQLException {
        if (patientRepository.findById(patientId).isEmpty()) {
            throw new ValidationException("patientId " + patientId + " does not exist.");
        }
    }

    private void ensureDoctorExists(String doctorId, String fieldName) throws SQLException {
        if (doctorId == null || doctorId.isBlank()) {
            return;
        }
        if (doctorRepository.findById(doctorId).isEmpty()) {
            throw new ValidationException(fieldName + " " + doctorId + " does not exist.");
        }
    }

    private void ensureDrugExists(String drugId) throws SQLException {
        if (drugRepository.findById(drugId).isEmpty()) {
            throw new ValidationException("drugId " + drugId + " does not exist.");
        }
    }

    private void ensureClaimExists(String claimId) throws SQLException {
        if (claimRepository.findById(claimId).isEmpty()) {
            throw new ValidationException("claimId " + claimId + " does not exist.");
        }
    }

    private List<PrescriptionReview> buildPrescriptionReviews(List<Prescription> prescriptions) throws SQLException {
        LocalDate today = LocalDate.now();
        List<PrescriptionReview> reviews = new ArrayList<>();

        for (Prescription prescription : prescriptions) {
            int durationDays = ValidationUtils.requirePositiveInteger(prescription.getDuration(), "duration");
            LocalDate endDate = prescription.getDatePrescribed().plusDays(durationDays - 1L);
            Drug drug = drugRepository.findById(prescription.getDrugId()).orElse(null);
            Doctor doctor = doctorRepository.findById(prescription.getDoctorId()).orElse(null);
            List<String> eligibilityIssues = determineEligibilityIssues(prescription, drug, doctor, durationDays);
            reviews.add(new PrescriptionReview(
                    prescription,
                    drug,
                    doctor,
                    endDate,
                    !endDate.isBefore(today),
                    eligibilityIssues.isEmpty(),
                    List.copyOf(eligibilityIssues)
            ));
        }

        return reviews.stream()
                .sorted((left, right) -> right.prescription().getDatePrescribed()
                        .compareTo(left.prescription().getDatePrescribed()))
                .collect(Collectors.toList());
    }

    private void ensurePrescriptionExists(String prescriptionId) throws SQLException {
        if (prescriptionRepository.findById(prescriptionId).isEmpty()) {
            throw new ValidationException("prescriptionId " + prescriptionId + " does not exist.");
        }
    }

    private void ensureVisitExists(VisitKey visitKey) throws SQLException {
        if (visitRepository.findById(visitKey).isEmpty()) {
            throw new ValidationException("visit " + visitKey.patientId() + "/" + visitKey.doctorId()
                    + "/" + visitKey.dateOfVisit() + " does not exist.");
        }
    }

    private Claim requireClaim(String claimId) throws SQLException {
        return claimRepository.findById(ValidationUtils.requireNonBlank(claimId, "claimId"))
                .orElseThrow(() -> new ValidationException("claimId " + claimId + " does not exist."));
    }

    private void populateClaimReferences(Claim claim) throws SQLException {
        Prescription prescription = prescriptionRepository.findById(claim.getPrescriptionId())
                .orElseThrow(() -> new ValidationException("prescriptionId " + claim.getPrescriptionId() + " does not exist."));
        Patient patient = patientRepository.findById(prescription.getPatientId())
                .orElseThrow(() -> new ValidationException("patientId " + prescription.getPatientId() + " does not exist."));
        if (patient.getInsuranceId() == null || patient.getInsuranceId().isBlank()) {
            throw new ValidationException("patientId " + patient.getPatientId() + " has no insurance coverage for claim creation.");
        }
        claim.setPatientId(patient.getPatientId());
        claim.setInsuranceId(patient.getInsuranceId());
    }

    private ClaimStatus parseClaimStatus(String status) {
        try {
            return ClaimStatus.valueOf(ValidationUtils.requireNonBlank(status, "status").toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("status must be one of: DRAFT, SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, PAID.");
        }
    }

    private void ensureClaimStatus(Claim claim, ClaimStatus... allowedStatuses) {
        ClaimStatus currentStatus = parseClaimStatus(claim.getStatus());
        for (ClaimStatus allowedStatus : allowedStatuses) {
            if (currentStatus == allowedStatus) {
                return;
            }
        }
        String allowed = List.of(allowedStatuses).stream().map(Enum::name).collect(Collectors.joining(", "));
        throw new ValidationException("claimId " + claim.getClaimId()
                + " must be in status " + allowed + " but is " + currentStatus.name() + ".");
    }

    private List<ClaimView> buildClaimViews(List<Claim> claims) throws SQLException {
        List<ClaimView> views = new ArrayList<>();
        for (Claim claim : claims) {
            Prescription prescription = prescriptionRepository.findById(claim.getPrescriptionId())
                    .orElseThrow(() -> new ValidationException("prescriptionId " + claim.getPrescriptionId() + " does not exist."));
            // Reuse the prescription review builder so claim pages and review pages share the same rules.
            PrescriptionReview review = buildPrescriptionReviews(List.of(prescription)).getFirst();
            views.add(new ClaimView(claim, review));
        }
        return views;
    }

    private List<String> determineEligibilityIssues(Prescription prescription,
                                                    Drug drug,
                                                    Doctor doctor,
                                                    int durationDays) throws SQLException {
        List<String> issues = new ArrayList<>();
        Patient patient = patientRepository.findById(prescription.getPatientId()).orElse(null);

        if (patient == null) {
            issues.add("patient record is missing.");
            return issues;
        }

        if (patient.getInsuranceId() == null || patient.getInsuranceId().isBlank()) {
            issues.add("patient has no insurance coverage on file.");
        }

        if (doctor == null) {
            issues.add("prescribing doctor record is missing.");
        }

        if (drug == null) {
            issues.add("drug record is missing.");
        }

        if (!hasSupportingVisit(prescription)) {
            issues.add("no matching visit with the prescribing doctor exists within 30 days before the prescription date.");
        }

        if (durationDays > 30 && (prescription.getComment() == null || prescription.getComment().isBlank())) {
            issues.add("prescriptions longer than 30 days require a clinical comment for claim review.");
        }

        if (hasOverlappingPrescription(prescription)) {
            issues.add("overlaps another active prescription for the same drug.");
        }

        return issues;
    }

    private boolean hasSupportingVisit(Prescription prescription) throws SQLException {
        for (Visit visit : visitRepository.findByPatientId(prescription.getPatientId())) {
            if (!prescription.getDoctorId().equals(visit.getDoctorId())) {
                continue;
            }
            if (visit.getDateOfVisit().isAfter(prescription.getDatePrescribed())) {
                continue;
            }

            // Prescriptions are considered supported only when the visit is on or before
            // the prescription date and falls within the prior 30-day window.
            long daysBetween = ChronoUnit.DAYS.between(visit.getDateOfVisit(), prescription.getDatePrescribed());
            if (daysBetween <= 30) {
                return true;
            }
        }
        return false;
    }

    private boolean hasOverlappingPrescription(Prescription prescription) throws SQLException {
        int durationDays = ValidationUtils.requirePositiveInteger(prescription.getDuration(), "duration");
        LocalDate newPrescriptionEndDate = prescription.getDatePrescribed().plusDays(durationDays - 1L);

        for (Prescription existing : prescriptionRepository.findByPatientId(prescription.getPatientId())) {
            if (prescription.getPrescriptionId().equals(existing.getPrescriptionId())) {
                continue;
            }
            if (!prescription.getDrugId().equals(existing.getDrugId())) {
                continue;
            }

            int existingDurationDays = ValidationUtils.requirePositiveInteger(existing.getDuration(), "duration");
            // Inclusive range comparison keeps same-day start and end dates from slipping through.
            LocalDate existingEndDate = existing.getDatePrescribed().plusDays(existingDurationDays - 1L);
            boolean overlaps = !existing.getDatePrescribed().isAfter(newPrescriptionEndDate)
                    && !existingEndDate.isBefore(prescription.getDatePrescribed());

            if (overlaps) {
                return true;
            }
        }
        return false;
    }
}
