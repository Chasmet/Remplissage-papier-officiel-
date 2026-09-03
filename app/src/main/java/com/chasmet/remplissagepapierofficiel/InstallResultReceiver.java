package com.chasmet.remplissagepapierofficiel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

public class InstallResultReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String rawMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        String otherPackage = intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME);
        String detail = rawMessage == null ? "" : rawMessage.trim();
        if (otherPackage != null && !otherPackage.trim().isEmpty()) {
            detail = (detail.isEmpty() ? "" : detail + " • ") + "Package concerné : " + otherPackage;
        }

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            UpdateManager.saveInstallStatus(context,
                    "Confirmation Android requise",
                    detail.isEmpty() ? "Validez l'installation dans l'écran Android." : detail);
            Intent confirmation = getConfirmationIntent(intent);
            if (confirmation != null) {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(confirmation);
                } catch (Exception e) {
                    AppLog.write(context, "installConfirmation", e);
                    UpdateManager.saveInstallStatus(context,
                            "Impossible d'ouvrir la confirmation Android",
                            e.getMessage());
                }
            }
            return;
        }

        if (status == PackageInstaller.STATUS_SUCCESS) {
            UpdateManager.saveInstallStatus(context,
                    "Mise à jour installée",
                    "Android a validé le remplacement de l'application.");
            return;
        }

        String title;
        switch (status) {
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                title = "Installation annulée";
                break;
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                title = "Installation bloquée par Android";
                break;
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                title = "Conflit de package détecté";
                break;
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                title = "APK incompatible avec cet appareil";
                break;
            case PackageInstaller.STATUS_FAILURE_INVALID:
                title = "APK refusé ou invalide";
                break;
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                title = "Stockage insuffisant";
                break;
            default:
                if (Build.VERSION.SDK_INT >= 34 && status == PackageInstaller.STATUS_FAILURE_TIMEOUT) {
                    title = "Installation expirée";
                } else {
                    title = "Échec de l'installation Android";
                }
                break;
        }

        if (detail.isEmpty()) detail = "Code Android : " + status;
        UpdateManager.saveInstallStatus(context, title, detail);
        AppLog.write(context, "packageInstaller status=" + status + " detail=" + detail,
                new IllegalStateException(title));
    }

    @SuppressWarnings("deprecation")
    private static Intent getConfirmationIntent(Intent source) {
        if (Build.VERSION.SDK_INT >= 33) {
            return source.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
        }
        return source.getParcelableExtra(Intent.EXTRA_INTENT);
    }
}
