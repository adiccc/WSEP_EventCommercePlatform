package application;

@FunctionalInterface
public interface AdmissionCallback {
    void onAdmitted(String uuid);
}
