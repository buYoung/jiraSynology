const innerviewLdap =  {
    ldapOpts: {
        url: 'ldap://innerviewit.com'
    },
    userDn: 'uid=root,cn=users,dc=innerviewit,dc=com',
    userPassword: '$inner20',
    userSearchBase: 'dc=innerviewit,dc=com',
    usernameAttribute: 'uid',
    attributes: ['uid', 'gecos', 'cn', 'sn', 'mail', 'displayName','departmentNumber','mobile', 'title'  ],
    groupClass: 'groupOfUniqueNames',
    groupMemberAttribute: 'memberOf',
}


module.exports = {
    innerviewLdap
}
