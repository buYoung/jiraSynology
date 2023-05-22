import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.changehistory.ChangeHistoryItem
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.event.issue.IssueEvent
import com.atlassian.jira.event.issue.link.IssueLinkCreatedEvent
import com.atlassian.jira.event.type.EventType
import groovy.json.JsonBuilder
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import groovyx.net.http.ContentType
import static groovyx.net.http.ContentType.URLENC



def applicationProperties = ComponentAccessor.getApplicationProperties()


def projectLength = projects.size()
def projectName = projects[projects.size() - 1]
if (projectName == "") {
    try {
        projectName = getProjectName([projectName, "",event])
    } catch (e) {
        projectName = getProjectName([projectName, "",event, issue])
    }
}
def baseUrl = "${applicationProperties.getString("jira.baseurl")}/projects/${projectName}/issues"
def defaultInfo = [projectName, baseUrl, event]
try {
    defaultInfo.add(issue)
} catch(e) {
    null
}
log.warn("defaultInfo ${defaultInfo}")

def issueTupleData = issueListenerGateWay(defaultInfo)
log.warn("getIssue Type ${issueTupleData}")

def templateString = templateGateway(issueTupleData)
log.warn("templateString ${templateString}")
def sendUserList = getSendUserList(issueTupleData, defaultInfo)
if (sendUserList == null) {
    return null
}
sendNotification(sendUserList[0], sendUserList[1], templateString)


def getSendUserList(issueTupleData, defaultInfo) {
    List<Long> eventTypes = [
            EventType.ISSUE_RESOLVED_ID,
            EventType.ISSUE_CLOSED_ID,
            EventType.ISSUE_REOPENED_ID,
            EventType.ISSUE_GENERICEVENT_ID
    ].asList()
    try {
        if (issueTupleData == null) {
            return null
        }
        if (issueTupleData == -1) {
            return null
        }
        if (issueTupleData[0] == -1) {
            return null
        }
        def userIds = []
        def userEmails = []
        def issueEventId = issueTupleData[0]
        def templateData = issueTupleData.subList(1, issueTupleData.size())
        // if (issueEventId == EventType.ISSUE_CREATED_ID) {
        //     return issueAssignedTemplate(tuple)
        // }
        if (issueEventId == EventType.ISSUE_UPDATED_ID) {
            // 이슈 우선순위 변경 템플릿
            def issueInfo = defaultInfo[3]
            return GetNotificationUser.setNotificationAssigneeAndReporter(issueInfo)
        }
        if (issueEventId == EventType.ISSUE_ASSIGNED_ID) {
            // 이슈 담당자 변경 템플릿
            def issueInfo = defaultInfo[3]
            return GetNotificationUser.setNotificationAssigneeAndReporter(issueInfo)
        }
        // if (issueEventId == EventType.ISSUE_RESOLVED_ID) {
        //     return "issueResolved"
        // }
        // if (issueEventId == EventType.ISSUE_REOPENED_ID) {
        //     return "issueReopened"
        // }
        if (eventTypes.contains((Long) issueEventId)) {
            // 이슈 작업흐름 변경 템플릿
            def issueInfo = defaultInfo[3]
            return GetNotificationUser.setNotificationAssigneeAndReporter(issueInfo)
        }
        if (issueEventId == EventType.ISSUE_COMMENTED_ID || issueEventId == EventType.ISSUE_COMMENT_EDITED_ID) {
            // 이슈 덧글 템플릿
            def issueInfo = defaultInfo[3]
            return GetNotificationUser.setNotificationAssigneeAndReporter(issueInfo)
        }
        if (issueEventId == 1000) {
            // 이슈 연결 템플릿
            // 이슈 연결은 데이터가 좀 다름
            def event = defaultInfo[2]
            def issueLink = event.getIssueLink()
            def issueLinkSourceId = issueLink.getSourceId()
            def issueManager = ComponentAccessor.getIssueManager()
            def sourceIssue = issueManager.getIssueObject(issueLinkSourceId)
            return GetNotificationUser.setNotificationAssigneeAndReporter(sourceIssue)
        }
        if (issueEventId == 1001) {
            // 이슈 sub 담당자 템플릿
            def changeUser = templateData[3]
            def oldUser = templateData[4]
            List<String> sendUserKeyList = []
            if (changeUser.size() > oldUser.size()) {
                sendUserKeyList = changeUser.minus(oldUser)
            }
            def issueInfo = defaultInfo[3]
            return GetNotificationUser.setNotificationAssigneeAndReporterAndSubAssignee(issueInfo, sendUserKeyList)
        }
        return [userIds, userEmails]
    } catch(e) {
        log.warn e
        return null
    }
}

def issueListenerGateWay(defaultInfo) {
    def event = defaultInfo[2]
    if (event instanceof IssueEvent) {
        log.warn("issueEvent임")
        def eventType = issueListenerEvent(defaultInfo)
        if (eventType == null) {
            return -1
        }
        log.warn("get IssueEvent eventType ${eventType}")
        return eventType
    }
    if (event instanceof IssueLinkCreatedEvent) {
        log.warn("IssueLinkCreatedEvent임")
        def eventType = issueLinkListenerEvent(defaultInfo)
        if (eventType == null) {
            return -2
        }
        log.warn("get IssueLinkCreatedEvent eventType ${eventType}")
        return eventType
    }
    log.warn("issueEvent가 아님")
    return -3
}
def issueListenerEvent(defaultInfo) {
    def projectName = defaultInfo[0]
    def baseUrl = defaultInfo[1]
    def event = defaultInfo[2]
    def issue = defaultInfo[3]
    List<Long> eventTypes = [
            EventType.ISSUE_RESOLVED_ID,
            EventType.ISSUE_CLOSED_ID,
            EventType.ISSUE_REOPENED_ID,
            EventType.ISSUE_GENERICEVENT_ID
    ].asList()

    def eventTypeId = event.eventTypeId
    def changeLog = event.changeLog
    def changeUser = GetUser.getChangedUserData(changeLog)
    def assigneeData = GetUser.getIssueAssignee(issue)
    def reporterData = GetUser.getIssueReporter(issue)

    // if (eventTypeId == EventType.ISSUE_CREATED_ID) {
    //     log.warn("이슈가 생성됨")
    //     return (int) EventType.ISSUE_CREATED_ID
    // }
    if (eventTypeId == EventType.ISSUE_COMMENTED_ID) {
        log.warn("이슈가 코멘트 작성됨")
        def comment = event.getComment()
        def commentBody = comment.getBody()
        def commentWriter = comment.getAuthorFullName()
        def issueInfo = comment.getIssue()
        def projectObject = issueInfo.getProjectObject()
        projectName = projectObject.getName()
        return [(int) EventType.ISSUE_COMMENTED_ID, projectName, issue.getSummary(),"${baseUrl}/${issue.key}", commentWriter, commentBody]
    }
    if (eventTypeId == EventType.ISSUE_COMMENT_EDITED_ID) {
        log.warn("이슈가 코멘트 수정됨")
        // def mentionedUsernames = []
        def comment = event.getComment()
        def commentChangeUser = comment.getUpdateAuthorFullName()
        def commentBody = comment.getBody()
        def issueInfo = comment.getIssue()
        def projectObject = issueInfo.getProjectObject()
        projectName = projectObject.getName()
        // log.warn("commentWriter ${comment.getAuthorFullName}")
        // log.warn("commentWriter ${comment.getAuthorApplicationUser().getDisplayName()}")
        // def matcher = (commentBody =~ /(?<!\w)~[a-zA-Z0-9_.-]+/)

        // while (matcher.find()) {
        //        mentionedUsernames << matcher.group().substring(1) // '@'를 제외한 사용자 이름을 얻습니다.
        // }
        // mentionedUsernames = mentionedUsernames.toSet().toList()

        return [(int) EventType.ISSUE_COMMENT_EDITED_ID, projectName, issue.getSummary(),"${baseUrl}/${issue.key}", commentChangeUser, commentBody]
    }
    def changeLogData = GetUser.getIssueChangeData(issue, changeLog)

    if (changeLogData == null) {
        log.warn "error?"
        return null
    }
    def changeLogType = changeLogData[0]
    def changeLogValue = changeLogData[1]
    def chnageLogFromValue = changeLogData[2]
    def changeLogUserKor = changeLogData[3]
    def changeLogUserEng = changeLogData[4]

    if (eventTypeId == EventType.ISSUE_UPDATED_ID) {
        log.warn("이슈가 수정됨")
        if (changeLogType == "assignee") {
            return null
        }
        // if (changeLogType == "priority") {
        //     return [(int) EventType.ISSUE_UPDATED_ID, projectName, issue.getSummary(), "${baseUrl}/${issue.getKey()}", changeLogValue, chnageLogFromValue]
        // }
        if (changeLogType == "SUB 담당자") {
            changeLogDataUserKey = GetUser.getIssueChangeDataUserKey(issue, changeLog)
            if (changeLogDataUserKey == null) {
                return null
            }
            changeLogType = changeLogDataUserKey[0]
            changeLogValue = changeLogDataUserKey[1]
            chnageLogFromValue = changeLogDataUserKey[2]
            changeLogUserKor = changeLogDataUserKey[3]
            changeLogUserEng = changeLogDataUserKey[4]
            return [1001, projectName, issue.getSummary(), "${baseUrl}/${issue.getKey()}", changeLogValue, chnageLogFromValue]
        }
        return [-1]
    }
    if (eventTypeId == EventType.ISSUE_ASSIGNED_ID) {
        log.warn("이슈가 담당자 변경")

        return [(int) EventType.ISSUE_ASSIGNED_ID, projectName, issue.getSummary(), "${baseUrl}/${issue.getKey()}", changeLogValue, chnageLogFromValue]
    }

    // if (eventTypeId == EventType.ISSUE_RESOLVED_ID) {
    //     log.warn("이슈가 완료됨")
    //     return (int) EventType.ISSUE_RESOLVED_ID
    // }
    // if (eventTypeId == EventType.ISSUE_REOPENED_ID) {
    //     log.warn("이슈가 다시열림")
    //     return (int) EventType.ISSUE_REOPENED_ID
    // }
    if (eventTypes.contains(eventTypeId)) {
        log.warn("이슈 일반 이벤트")
        return [(int) eventTypeId, projectName, issue.getSummary(), "${baseUrl}/${issue.getKey()}", changeLogUserKor, changeLogValue, chnageLogFromValue]
    }
    return -1
}

def issueLinkListenerEvent(defaultInfo) {
    def projectName = defaultInfo[0]
    def baseUrl = defaultInfo[1]
    def event = defaultInfo[2]

    def issueLink = event.getIssueLink()
    def issueLinkSourceId = issueLink.getSourceId()
    def issueLinkDestinationId = issueLink.getDestinationId()
    def issueManager = ComponentAccessor.getIssueManager()

    def sourceIssue = issueManager.getIssueObject(issueLinkSourceId)
    def destinationIssue = issueManager.getIssueObject(issueLinkDestinationId)
    def destinationAssignee = GetUser.getIssueAssignee(sourceIssue)


    if (destinationAssignee == null) {
        destinationAssignee = "없음"
    }
    def issueUrl = "${baseUrl}/${sourceIssue.getKey()}"
    def isisueDestinationUrl = "${baseUrl}/${destinationIssue.getKey()}"

    return [1000, projectName, destinationIssue.getSummary(), isisueDestinationUrl, sourceIssue.getSummary(), issueUrl]
}


def sendNotification(List userIds, List userEmails, String body) {
    def url = "http://notification.innerviewit.com";
    def http = new HTTPBuilder(url)
    def jsonBuilder = new JsonBuilder()

    http.request( Method.POST, ContentType.JSON ) {
        uri.path = '/notification/jira'
        send URLENC, [
                message: body,
                user_ids: [*userIds],
                user_emails: [*userEmails]
        ]
        response.success = { resp, json ->
            log.warn "POST request succeeded"
            log.warn "Response status: ${resp.statusLine}"
            log.warn "Response headers: ${resp.headers}"
            log.warn "Response body: ${json}"
        }
        response.error = { resp, json ->
            log.warn "POST request failed"
            log.warn "Response status: ${resp.statusLine}"
            log.warn "Response headers: ${resp.headers}"
        }
        timeout = 3000
        throwOnError = false
    }
}
def getProjectName(ArrayList defaultInfo){
    def projectName = defaultInfo[0]
    if (projectName != "") {
        return projectName
    }
    def event = defaultInfo[2]
    Issue issue
    if (defaultInfo.size() == 4) {
        issue = defaultInfo[3]
    } else {
        def issueLink = event.getIssueLink()
        def issueLinkSourceId = issueLink.getSourceId()
        def issueManager = ComponentAccessor.getIssueManager()
        issue = issueManager.getIssueObject(issueLinkSourceId)
    }
    
    def project = issue.getProjectObject()
    projectName = project.getKey()
    if (projectName != "") {
        return projectName
    }
    def projectId = issue.getProjectId()
    def projectManager = ComponentAccessor.getProjectManager()
    def projectObject = projectManager.getProjectObj(projectId)

    projectName = projectObject.getKey()
    return projectName
}

def templateGateway(issueTupleData) {
    if (issueTupleData == null) {
        return null
    }
    if (issueTupleData == -1) {
        return null
    }
    if (issueTupleData[0] == -1) {
        return null
    }
    List<Long> eventTypes = [
        EventType.ISSUE_RESOLVED_ID,
        EventType.ISSUE_CLOSED_ID,
        EventType.ISSUE_REOPENED_ID,
        EventType.ISSUE_GENERICEVENT_ID
    ].asList()

    def issueEventId = issueTupleData[0]
    def templateData = issueTupleData.subList(1, issueTupleData.size())
    // if (issueEventId == EventType.ISSUE_CREATED_ID) {
    //     return Template.issueAssignedTemplate(tuple)
    // }
    if (issueEventId == EventType.ISSUE_UPDATED_ID) {

        return Template.issuePriorityUpdatedTemplate(templateData)
    }
    if (issueEventId == EventType.ISSUE_ASSIGNED_ID) {
        return Template.issueAssigneeUpdateTemplate(templateData)
    }
    // if (issueEventId == EventType.ISSUE_RESOLVED_ID) {
    //     return "issueResolved"
    // }
    // if (issueEventId == EventType.ISSUE_REOPENED_ID) {
    //     return "issueReopened"
    // }
    if (eventTypes.contains((Long) issueEventId)) {
        log.warn templateData
        return Template.issueUpdatedForWorkflowTemplate(templateData)
    }
    if (issueEventId == EventType.ISSUE_COMMENTED_ID || issueEventId == EventType.ISSUE_COMMENT_EDITED_ID) {
        return Template.issueCommentedTemplate(templateData)
    }
    if (issueEventId == 1000) {
        // 이슈 연결 템플릿
        return Template.issueLinkTemplate(templateData)
    }
    if (issueEventId == 1001) {
        // 이슈 sub 담당자 변경 템플릿
        return Template.issueSubAssigneeUpdateTemplate(templateData)
    }
}

class GetNotificationUser {
    // 담당자만 알림
    static def setNotificationAssignee(issueInfo) {
        def assigneeUser = getIssueAssignee(issueInfo)
        def userIds = []
        def userEmails = []

        if (assigneeUser == null) {
            return [userIds, userEmails]
        }

        userIds = [
                assigneeUser[1]
        ].toSet().toList()
        userEmails = [
                assigneeUser[2]
        ].toSet().toList()
        return [userIds, userEmails]
    }

// 보고자만 알림
    static def setNotificationReporter(issueInfo) {
        def reporterUser = GetUser.getIssueReporter(issueInfo)
        def userIds = []
        def userEmails = []

        if (reporterUser == null) {
            return [userIds, userEmails]
        }

        userIds = [
                reporterUser[1]
        ].toSet().toList()
        userEmails = [
                reporterUser[2]
        ].toSet().toList()
        return [userIds, userEmails]
    }

// 담당자, 보고자 알림
    static def setNotificationAssigneeAndReporter(issueInfo) {
        def assigneeUser = GetUser.getIssueAssignee(issueInfo)
        def reporterUser = GetUser.getIssueReporter(issueInfo)
        def userIds = []
        def userEmails = []

        try { // 이슈 상위작업 찾기
            def currentIssue = issueInfo
            while(true) {
                def isSubTask = isIssueSubTask(currentIssue)
                if (isSubTask) {
                    currentIssue = currentIssue.getParentObject()
                    def subTaskAssigneeUser = getIssueAssignee(currentIssue)
                    def subTaskReporterUser = GetUser.getIssueReporter(currentIssue)
                    if (subTaskAssigneeUser != null) {
                        userIds.add(subTaskAssigneeUser[1])
                        userEmails.add(subTaskAssigneeUser[2])
                    }
                    if (subTaskReporterUser != null) {
                        userIds.add(subTaskReporterUser[1])
                        userEmails.add(subTaskReporterUser[2])
                    }
                } else {
                    break
                }
            }
        } catch(e) {

        }
        try { // 이슈 연결 찾기
            def issueLinkManager = ComponentAccessor.getIssueLinkManager()
            def issueLinks = issueLinkManager.getLinkCollectionOverrideSecurity(issueInfo)
            def issueLinksRelates = issueLinks.getInwardIssues("Relates")

            for (issueLinkRelate in issueLinksRelates) {
                def issueLinkAssigneeUser = getIssueAssignee(issueLinkRelate)
                def issueLinkReporterUser = GetUser.getIssueReporter(issueLinkRelate)
                if (issueLinkAssigneeUser != null) {
                    userIds.add(issueLinkAssigneeUser[1])
                    userEmails.add(issueLinkAssigneeUser[2])
                }
                if (issueLinkReporterUser != null) {
                    userIds.add(issueLinkReporterUser[1])
                    userEmails.add(issueLinkReporterUser[2])
                }
            }
        } catch(e) {
        }
        try {
            def customFieldManager = ComponentAccessor.getCustomFieldManager()
            def customFieldObject = customFieldManager.getCustomFieldObjects(issueInfo).find{
                it.getFieldName().toLowerCase() == "sub 담당자"
            }
            if (customFieldObject != null) {
                def selectedUsers = (ArrayList<ApplicationUser>) issueInfo.getCustomFieldValue(customFieldObject)
                if (selectedUsers != null) {
                    selectedUsers.each { user ->
                        def userInfo = GetUser.getUserInfoData(user)
                        userIds.add(userInfo[1])
                        userEmails.add(userInfo[2])
                    }
                }
            }
        } catch(e) {}
        if (assigneeUser != null) {
            userIds.add(assigneeUser[1])
            userEmails.add(assigneeUser[2])
        }
        if (reporterUser != null) {
            userIds.add(reporterUser[1])
            userEmails.add(reporterUser[2])
        }

        userIds = userIds.toSet().toList() // 중복제거
        userEmails = userEmails.toSet().toList() // 중복제거
        return [userIds, userEmails]
    }

// 담당자, 보고자 알림
    static def setNotificationAssigneeAndReporterAndSubAssignee(issueInfo, sendUserKeyList) {
        def notificationAssigneeAndReporter = setNotificationAssigneeAndReporter(issueInfo)
        def userIds = notificationAssigneeAndReporter[0]
        def userEmails = notificationAssigneeAndReporter[1]
        if (userIds.size() == 0) {
            return [userIds, userEmails]
        }
        if (userEmails.size() == 0) {
            return [userIds, userEmails]
        }
        def userManager = ComponentAccessor.getUserManager()
        sendUserKeyList.each { userKey ->
            def user = userManager.getUserByKey(userKey)
            userIds.add(user.getName())
            userEmails.add(user.getEmailAddress())
        }
        userIds = userIds.toSet().toList() // 중복제거
        userEmails = userEmails.toSet().toList() // 중복제거
        return [userIds, userEmails]
    }

    static private def isIssueSubTask(issueInfo) {
        try {
            def checkIfParentIssue = issueInfo.getIssueType()
            return checkIfParentIssue.isSubTask()
        } catch(e) {
            return false
        }
    }
}

class GetUser {
    static def getIssueAssignee(issue) {
        def assignee = issue.getAssignee()
        if (assignee == null) {
            return null
        }

        return getUserInfoData(assignee)
    }

    static def getIssueReporter(issue) {
        def reporter = issue.getReporter()
        if (reporter == null) {
            return null
        }

        return getUserInfoData(reporter)
    }

    static def getUserInfoData(userInfo) {
        def name = userInfo.getDisplayName()
        def nameEng = userInfo.getName()
        def email = userInfo.getEmailAddress()
        return [name, nameEng, email]
    }

    static def getChangedUserData(changeLog) {
        if (changeLog == null) {
            return null
        }

        def authorKey = changeLog.get("author")
        def changedUser = ComponentAccessor.userManager.getUserByKey(authorKey)
        if (changedUser == null) {
            return null
        }

        return getUserInfoData(changedUser)
    }
    static def getIssueChangeData(issue, changeLog) {
        def changeHistoryManager = ComponentAccessor.getChangeHistoryManager()
        def changeItems = changeHistoryManager.getAllChangeItems(issue)
        if (changeItems == null || changeItems.size() == 0) {
            return null
        }
        def findChangeHistoryData = changeItems.last()
        if (findChangeHistoryData == null || findChangeHistoryData.getField() == null) {
            return null
        }
        def changeItemBeans = changeHistoryManager.getChangeItemsForField(issue, "status")
        if (changeItemBeans == null || changeItemBeans.size() == 0) {
            return null
        }
        
        def findChangeHistoryBeanData = changeItemBeans.last()
        if (findChangeHistoryBeanData == null) {
            return null
        }
        def changedToValue = findChangeHistoryBeanData.getToString()
        def changedFromValue = findChangeHistoryBeanData.getFromString()

        def userManager = ComponentAccessor.getUserManager()
        def userKey = findChangeHistoryData.getUserKey()
        def userData = userManager.getUserByKey(userKey)

        def userDataTuple = getUserInfoData(userData)
        def status = findChangeHistoryData.getField()

        def parseChangeHistoryUsers = parseChangeHistoryValues(findChangeHistoryData)
        def changeValues = parseChangeHistoryUsers[0]
        def changeValueSize = parseChangeHistoryUsers[1]
        def changeFromValues = parseChangeHistoryUsers[2]
        def changeFromValueSize = parseChangeHistoryUsers[3]

        def changeValue = []
        def changeFromValue = []

        if (!changedToValue.isEmpty()) {
            changeValue.add(changedToValue)
        }
        if (!changedFromValue.isEmpty()) {
            changeFromValue.add(changedFromValue)
        }

        if (changeValue.isEmpty() && changeFromValue.isEmpty()) {
            return [status, "없음", "없음", userDataTuple[0], userDataTuple[1]]
        }
        if (changeValue.isEmpty()) {
            return [status, "없음", changeFromValue, userDataTuple[0], userDataTuple[1]]
        }
        if (changeFromValue.isEmpty()) {
            return [status, changeValue,  "없음", userDataTuple[0], userDataTuple[1]]
        }
        return [status, changedToValue, changedFromValue, userDataTuple[0], userDataTuple[1]]
    }
    static def getIssueChangeDataUserKey(issue, changeLog) {
        def changeHistoryManager = ComponentAccessor.getChangeHistoryManager()
        def changeItems = changeHistoryManager.getAllChangeItems(issue)
        def findChangeHistoryData = changeItems.last()
        if (findChangeHistoryData == null) {
            return null
        }
        def userManager = ComponentAccessor.getUserManager()
        def userKey = findChangeHistoryData.getUserKey()
        def userData = userManager.getUserByKey(userKey)
        def userDataTuple = getUserInfoData(userData)
        def status = findChangeHistoryData.getField()

        def parseChangeHistoryUsers = parseChangeHistoryUsers(findChangeHistoryData)
        def changeValues = parseChangeHistoryUsers[0]
        def changeValueSize = parseChangeHistoryUsers[1]
        def changeFromValues = parseChangeHistoryUsers[2]
        def changeFromValueSize = parseChangeHistoryUsers[3]

        List<String> changeValue = []
        List<String> changeFromValue = []

        if (changeValueSize >= 1) {
            changeValues.each { value ->
                changeValue.add(value)
            }
        }
        if (changeFromValueSize >= 1) {
            changeFromValues.each { value ->
                changeFromValue.add(value)
            }
        }
        return [status, changeValue, changeFromValue, userDataTuple[0], userDataTuple[1]]
    }
    static private def parseChangeHistoryValues(ChangeHistoryItem historyItem) {
        Map<String, String> fromMap = parseHistoryItem(historyItem.getFroms())
        Map<String, String> toMap = parseHistoryItem(historyItem.getTos())

        List<String> changedValues = toMap.values().toArray()
        List<String> changedFromValues = fromMap.values().toArray()
        int changedValuesSize = changedValues.size()
        int changedFromValuesSize = changedFromValues.size()
        return [changedValues, changedValuesSize, changedFromValues, changedFromValuesSize]
    }
    static private def parseChangeHistoryUsers(ChangeHistoryItem historyItem) {
        Map<String, String> fromMap = parseHistoryItem(historyItem.getFroms())
        Map<String, String> toMap = parseHistoryItem(historyItem.getTos())

        List<String> changedValues = toMap.keySet().toArray()
        List<String> changedFromValues = fromMap.keySet().toArray()
        int changedValuesSize = changedValues.size()
        int changedFromValuesSize = changedFromValues.size()
        return [changedValues, changedValuesSize, changedFromValues, changedFromValuesSize]
    }
    
    static private def parseHistoryItem(map) {
        if (map == null) {
            return [:]
        }
        if (map.size() == 0) {
            return [:]
        }
        def result = [:]

        map.each { k, v ->
            try {
                def keys = k.substring(1, k.length() - 1).tokenize(", ")
                def values = v.tokenize(", ")

                keys.eachWithIndex { key, index ->
                    result[key] = values[index].trim()
                }
            } catch (e){}
        }
        return result
    }
}

class Template {
    /*
* 이슈 담당자 변경 템플릿
* @param [List] issueAssignedTuple
* @param [String] 프로젝트명
* @param [String] 이슈 제목
* @param [String] 이슈 URL
* @param [String] 변경된 담당자
* @param [String] 이전 담당자
*/
    static def issueAssigneeUpdateTemplate(issueAssignedTuple) {
        def projectName = issueAssignedTuple[0]
        def issueTitle = issueAssignedTuple[1]
        def issueUrl = issueAssignedTuple[2]
        def issueChangedAssignee = issueAssignedTuple[3]
        def issuePreviousAssignee = issueAssignedTuple[4]

        return """
프로젝트명 : *${projectName}*

*${issueTitle}* 의 담당자가 ${issueChangedAssignee}로 변경되었습니다.

이전 담당자 : ${issuePreviousAssignee} -> 현재 담당자 : ${issueChangedAssignee}

이슈 확인하기 : <${issueUrl}|지라로이동하기>
""".stripIndent()
    }
/*
* 이슈 sub 담당자 변경 템플릿
* @param [List] issueSubAssignedTuple
* @param [String] 프로젝트명
* @param [String] 이슈 제목
* @param [String] 이슈 URL
*/
    static def issueSubAssigneeUpdateTemplate(issueSubAssignedTuple) {
        def projectName = issueSubAssignedTuple[0]
        def issueTitle = issueSubAssignedTuple[1]
        def issueUrl = issueSubAssignedTuple[2]

        return """
프로젝트명 : *${projectName}*

*${issueTitle}* 의 서브 담당자가 업데이트 되었습니다.

이슈 확인하기 : <${issueUrl}|지라로이동하기>
""".stripIndent()
    }

/*
* 이슈 작업흐름 변경 템플릿
* @param [List] issueUpdateWorkflowTuple
* @param [String] 프로젝트명
* @param [String] 이슈 제목
* @param [String] 이슈 URL
* @param [String] 변경한 사용자
* @param [String] 변경된 작업흐름
* @param [String] 이전 작업흐름
*/
    static def issueUpdatedForWorkflowTemplate(issueUpdateWorkflowTuple) {
        def projectName = issueUpdateWorkflowTuple[0]
        def issueTitle = issueUpdateWorkflowTuple[1]
        def issueUrl = issueUpdateWorkflowTuple[2]
        def issueChangeUser = issueUpdateWorkflowTuple[3]
        def issueChangedWorkflow = issueUpdateWorkflowTuple[4]
        def issuePreviousWorkflow = issueUpdateWorkflowTuple[5]

        return """
프로젝트명 : *${projectName}*

*${issueTitle}* 의 작업흐름이  ${issueChangeUser}님에 의해 ${issueChangedWorkflow}로 변경되었습니다.

이전 상태 : ${issuePreviousWorkflow} -> 현재 상태 : ${issueChangedWorkflow}

이슈 확인하기 : <${issueUrl}|지라로이동하기>
""".stripIndent()
    }

/*
* 이슈 우선순위 변경 템플릿
* @param [List] issuePriorityTuple
* @param [String] 프로젝트명
* @param [String] 이슈 제목
* @param [String] 이슈 URL
* @param [String] 이슈 변경된 우선순위
* @param [String] 이슈 이전 우선순위
*/
    static def issuePriorityUpdatedTemplate(issuePriorityTuple) {
        def projectName = issuePriorityTuple[0]
        def issueTitle = issuePriorityTuple[1]
        def issueUrl = issuePriorityTuple[2]
        def issueChangedPriority = issuePriorityTuple[3]
        def issuePreviousPriority = issuePriorityTuple[4]

        return """
프로젝트명 : *${projectName}*

*${issueTitle}* 의 우선순위가 ${issueChangedPriority}로 변경되었습니다.

이전 우선순위 : ${issuePreviousPriority} -> 현재 우선순위 : ${issueChangedPriority}

이슈 확인하기 : <${issueUrl}|지라로이동하기>
""".stripIndent()
    }

/*
* 이슈 연결 템플릿
* @param [List] issueLinkTuple
* @param [String] 프로젝트명
* @param [String] 이슈 제목
* @param [String] 이슈 url
* @param [String] 이슈 연결 대상 제목
* @param [String] 이슈 연결 대상 url
* @return [String]
*/
    static def issueLinkTemplate(issueLinkTuple) {
        def projectName = issueLinkTuple[0]
        def issueTitle = issueLinkTuple[1]
        def issueUrl = issueLinkTuple[2]
        def issueDestinationTitle = issueLinkTuple[3]
        def issueDestinationUrl = issueLinkTuple[4]

        return """
프로젝트명 : *${projectName}*

*${issueTitle}* 의 이슈가 *${issueDestinationTitle}* 에 연결되었습니다.

연결된 이슈 확인하기 : <${issueUrl}|지라로이동하기>
대상 이슈 확인하기 : <${issueDestinationUrl}|지라로이동하기>
""".stripIndent()
    }

// /*
// * 이슈 하위 작업 템플릿
// * @param [List] issueSubTaskTuple
// * @param [String] issueSubTaskTuple[0] 프로젝트명
// * @param [String] issueSubTaskTuple[1] 이슈 제목
// * @param [String] issueSubTaskTuple[2] 이슈 URL
// * @param [String] issueSubTaskTuple[3] 하위 이슈 제목
// * @param [String] issueSubTaskTuple[4] 하위 이슈 URL
// * @return [String]
// */
// static def issueSubTaskCreatedTemplate(issueSubTaskTuple) {
//     def projectName = issueSubTaskTuple[0]
//     def issueTitle = issueSubTaskTuple[1]
//     def issueUrl = issueSubTaskTuple[2]
//     def issueSubTaskTitle = issueSubTaskTuple[3]
//     def issueSubTaskUrl = issueSubTaskTuple[4]

//     def jsonBuilder = new JsonBuilder()
//     def jsonObject = jsonBuilder {
//         template: "
//         ${projectName} *${issueTitle}*의 이슈에 하위 이슈가 생성되었습니다.

//         상위 이슈 확인하기 : <${issueUrl}|지라로이동하기>
//         하위 이슈 확인하기 : <${issueSubTaskUrl}|지라로이동하기>
//         "
//     }
//     return jsonObject.toString()
// }

/*
* 이슈 덧글 템플릿
* @param [List] issueCommentTuple
* @param [String] issueCommentTuple[0] 프로젝트명
* @param [String] issueCommentTuple[1] 이슈 제목
* @param [String] issueCommentTuple[2] 이슈 URL
* @param [String] issueCommentTuple[3] 이슈 덧글 내용
* @return [String]
*/
    static def issueCommentedTemplate(issueCommentTuple) {
        def projectName = issueCommentTuple[0]
        def issueTitle = issueCommentTuple[1]
        def issueUrl = issueCommentTuple[2]
        def writer = issueCommentTuple[3]
        def body = issueCommentTuple[4]

        return """
프로젝트명 : *${projectName}*

*${issueTitle}* 의 이슈에 코멘트가 등록(수정)되었습니다.

작성자: ${writer}

```\n내용:\n${body}```

이슈 확인하기 : <${issueUrl}|지라로이동하기>
""".stripIndent()
    }

// /*
// * 이슈 완료 템플릿
// * @param [List] issueCommentTuple
// * @param [String] issueCommentTuple[0] 프로젝트명
// * @param [String] issueCommentTuple[1] 이슈 제목
// * @param [String] issueCommentTuple[2] 이슈 URL
// * @param [String] issueCommentTuple[3] 이슈 담당자
// * @param [String] issueCommentTuple[4] 이슈 완료 시간
// * @return String
// */
// static def issueComplatedTemplate(issueComplatedTuple) {
//     def projectName = issueComplatedTuple[0]
//     def issueTitle = issueComplatedTuple[1]
//     def issueUrl = issueComplatedTuple[2]
//     def issueAssignee = issueComplatedTuple[3]
//     def createdAt = issueComplatedTuple[4]

//     def jsonBuilder = new JsonBuilder()
//     def jsonObject = jsonBuilder {
//         template: "
//         ${projectName} *${issueTitle}*의 이슈가 완료되었습니다.

//         이슈 담당자인 ${issueAssignee}님께서 ${createdAt}에 작업흐름이 완료상태로 변경되었습니다.

//         이슈 확인하기 : <${issueUrl}|지라로이동하기>
//         "
//     }
//     return jsonObject.toString()
// }
}
