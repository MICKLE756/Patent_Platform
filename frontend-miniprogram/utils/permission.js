class PermissionManager {
  constructor() {
    this.permissions = {}
  }

  setPermissions(permissions) {
    this.permissions = permissions || {}
  }

  hasPermission(permissionKey) {
    return this.permissions[permissionKey] === 1
  }

  canAccessModule(moduleKey) {
    const modulePermissions = {
      'dashboard': ['permissionStatisticsView'],
      'message-management': ['permissionIntentionAudit', 'permissionNoticePublish', 'permissionLogView'],
      'admin-management': ['permissionEnterpriseView', 'permissionEnterpriseEdit', 'permissionEnterpriseDelete', 'permissionResearchView', 'permissionResearchEdit', 'permissionResearchDelete', 'permissionPatentAudit', 'permissionPatentReview'],
      'role-management': ['permissionSystemConfig'],
      'system-management': ['permissionSystemConfig']
    }

    const requiredPermissions = modulePermissions[moduleKey] || []
    if (requiredPermissions.length === 0) return true

    return requiredPermissions.some(perm => this.hasPermission(perm))
  }

  filterMenuByPermission(menuList) {
    return menuList.filter(menu => {
      return this.canAccessModule(menu.page)
    })
  }
}

const permissionManager = new PermissionManager()

module.exports = permissionManager