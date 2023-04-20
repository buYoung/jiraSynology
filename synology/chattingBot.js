const axios = require("axios");
const synoConst = require("./constants.js");

async function sendMessage(platform, user_ids, user_emails, message) {
    const sendMessageBody = {...synoConst.requestBody.sendMessage};
    sendMessageBody.text = message;
    if (typeof user_ids === "string" && typeof user_emails === "string") {
        try {
            const sendUserIds = [];
            sendUserIds.push(getUserNameToUserUserId(user_ids));
            sendUserIds.push(getUserNameToUserEmail(user_emails));

            // remove duplicate
            const uniqueSendUserIds = [...new Set(sendUserIds)];

            sendMessageBody.user_ids = uniqueSendUserIds
            const payload = `payload=${JSON.stringify(sendMessageBody)}`;
            console.log(payload);
            const response = await axios.post(synoConst.urls.chat, payload, {
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded'
                }
            });
            console.log(response.data);
        } catch (e) {
            console.log("error single",e);
        }
    } else {
        try {
            const sendUserIds = [];
            for (let userId of user_ids) {
                sendUserIds.push(getUserNameToUserUserId(userId));
            }
            for (let userEmail of user_emails) {
                sendUserIds.push(getUserNameToUserEmail(userEmail));
            }

            const uniqueSendUserIds = [...new Set(sendUserIds)];

            for (let uniqueSendUserId of uniqueSendUserIds) {
               try {
                   sendMessageBody.user_ids = [uniqueSendUserId];
                   const payload = `payload=${JSON.stringify(sendMessageBody)}`;
                   console.log(payload);
                   const response = await axios.post(synoConst.urls.chat, payload, {
                       headers: {
                           'Content-Type': 'application/x-www-form-urlencoded'
                       }
                   });
                   console.log(response.data);
               } catch (e) {
                     console.log("error multiple2",e);
               }
            }
        } catch (e) {
            console.log("error multiple",e);
        }
    }
}
async function getChatUserList() {
    const response = await axios.get(synoConst.urls.chatUserList, {
        params: {
            token: synoConst.token
        }
    });
    const responseData = response.data;
    const isSucceess = responseData.success;
    if (!isSucceess) {
        return;
    }
    const userDataList = responseData.data.users;
    const userList = [];
    for (const userData of userDataList) {
        const type = userData.type;
        const user_id = userData.user_id;
        const username = userData.username;
        const userEmail = userData.user_props.email;
        userList.push({
            type: type,
            user_id: user_id,
            username: username,
            email: userEmail
        });
    }
    global.chattingUserList = userList;
}

function getUserNameToUserEmail(emails) {
    const userList = global.chattingUserList;

    for (const user of userList) {
        if (user.email === emails) {
            return user.user_id;
        }
    }
    return null;
}

function getUserNameToUserUserId(userName) {
    const userList = global.chattingUserList;

    for (const user of userList) {
        if (user.username === userName) {
            return user.user_id;
        }
    }
    return null;
}
function getUserNamesToUserId(userNames) {
    const userList = global.chattingUserList;
    const userIds = [];
    for (const user of userList) {
        console.log(user);
        if (userNames.includes(user.email)) {
            userIds.push(user.user_id);
        }
    }
    return userIds;
}


module.exports = {
    sendMessage,
    getChatUserList
}

