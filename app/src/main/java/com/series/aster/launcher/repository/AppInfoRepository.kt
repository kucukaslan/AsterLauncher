package com.series.aster.launcher.repository

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.UserManager
import android.util.Log
import com.series.aster.launcher.Constants
import com.series.aster.launcher.data.dao.AppInfoDAO
import com.series.aster.launcher.data.entities.AppInfo
import com.series.aster.launcher.helper.AppHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject


class AppInfoRepository @Inject constructor(
    private val appDao: AppInfoDAO
) {

    @Inject
    lateinit var packageManager: PackageManager

    @Inject
    lateinit var appHelper: AppHelper

    fun getDrawApps(): Flow<List<AppInfo>> {
        return appDao.getDrawAppsFlow()
    }

    fun getFavoriteApps(): Flow<List<AppInfo>> {
        return appDao.getFavoriteAppsFlow()
    }

    fun getHiddenApps(): Flow<List<AppInfo>> {
        return appDao.getHiddenAppsFlow()
    }

    suspend fun updateInfo(appInfo: AppInfo) {
        appDao.update(appInfo)
    }

    suspend fun updateAppOrder(appInfoList: List<AppInfo>) {
        withContext(Dispatchers.IO) {
            appDao.updateAppOrder(appInfoList)
        }
    }

    fun searchNote(query: String?): Flow<List<AppInfo>> {
        return appDao.searchApps(query)
    }

    suspend fun updateFavoriteAppInfo(appInfo: AppInfo) = withContext(Dispatchers.IO) {

        if (appInfo.favorite) {
            // add to favorite
            val maxOrder = appDao.getMaxOrder()
            val newOrder = maxOrder + 1
            appInfo.appOrder = newOrder
            appDao.updateAppInfo(appInfo)
            Log.d("Tag", "${appInfo.appName} : DAO Order: ${appInfo.appOrder}")
            Log.d("Tag", "${appInfo.appName} : DAO Favorite: ${appInfo.favorite}")
        } else {
            // remove from favorite
            appInfo.appOrder = -1
            appDao.updateAppInfo(appInfo)
            Log.d("Tag", "${appInfo.appName} : DAO Order Remove: ${appInfo.appOrder}")
            Log.d("Tag", "${appInfo.appName} : DAO Favorite Remove: ${appInfo.favorite}")
        }

        val favoriteAppInfos = appDao.getFavoriteAppInfo().sortedBy { it.appOrder }

        // sort favorite and update order
        for ((index, info) in favoriteAppInfos.withIndex()) {
            info.appOrder = index
            appDao.updateAppInfo(info)
        }
    }

    suspend fun updateAppName(appInfo: AppInfo, newAppName: String) = withContext(Dispatchers.IO) {
        appInfo.appName = newAppName
        appDao.updateAppName(appInfo, newAppName)
    }

    suspend fun updateAppHidden(appInfo: AppInfo, appHidden: Boolean) =
        withContext(Dispatchers.IO) {
            appInfo.hidden = appHidden
            appDao.updateAppHidden(appInfo, appHidden)
        }

    suspend fun updateAppLock(appInfo: AppInfo, appLock: Boolean) = withContext(Dispatchers.IO) {
        appInfo.lock = appLock
        appDao.updateLockApp(appInfo, appLock)
    }

    private suspend fun getInstalledPackages(): Set<String> = withContext(Dispatchers.IO) {

        val packages = HashSet<String>()
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in apps) {
            packages.add(app.packageName)
        }
        packages
    }

    suspend fun initInstalledAppInfo(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
            val appList: MutableList<AppInfo> = mutableListOf()

            val allApps = appDao.getAllAppsFlow().firstOrNull()

            val existingPackageNames = allApps?.map { it.packageName } ?: emptyList()

            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            val launcherApps =
                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

            val excludedPackageName = Constants.PACKAGE_NAME

            val newAppList: List<AppInfo> = userManager.userProfiles
                .flatMap { profile ->
                    launcherApps.getActivityList(null, profile)
                        .mapNotNull { app ->
                            val packageName = app.applicationInfo.packageName
                            if (packageName !in existingPackageNames && packageName != excludedPackageName) {
                                AppInfo(
                                    appName = appHelper.getAppLabel(context, app.applicationInfo),
                                    packageName = packageName,
                                    favorite = false,
                                    hidden = false,
                                    lock = false
                                )
                            } else {
                                val existingApp = getAppByPackageName(packageName)
                                existingApp?.let { appList.add(it) }
                                existingApp
                            }
                        }
                }

            appDao.insertAll(newAppList.sortedBy { it.appName })
            Log.d("Tag", "State: ${newAppList.sortedBy { it.appName }}")

            val deletedApps = allApps?.filter { it.packageName !in existingPackageNames }
            deletedApps?.forEach { appDao.delete(it) }

            appList.sortBy { it.appName }
            appList
        }

    suspend fun compareInstalledApp(): List<AppInfo> = withContext(Dispatchers.IO) {
        val installedPackages = getInstalledPackages()
        val uninstalledApps = mutableListOf<AppInfo>()

        val allApps = appDao.getAllApps()

        val newApps = mutableListOf<AppInfo>()

        // compare application
        for (app in allApps) {
            val packageName = app.packageName
            if (!installedPackages.contains(packageName)) {
                // application uninstall
                appDao.delete(app)
                uninstalledApps.add(app)
            }
        }

        // query new application
        val newPackageNames = installedPackages.filterNot { packageName ->
            allApps.any { app -> app.packageName == packageName }
        }

        for (packageName in newPackageNames) {
            val app = appDao.getAppByPackageName(packageName)
            app?.let {
                newApps.add(app)
            }
        }

        appDao.insertAll(newApps)
        uninstalledApps
    }

    private suspend fun getAppByPackageName(packageName: String): AppInfo? {
        return appDao.getAppByPackageName(packageName)
    }
}