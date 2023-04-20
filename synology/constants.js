const token = 'eQmhyw2I397sPrmVXdtsmgJvqfST9jL84e4zTLtblX8z9LV5nlPE9TCid90Kevcv';
const urls = {
    login: 'https://nas.innerviewit.com/webapi/entry.cgi?api=SYNO.API.Auth',
    logout: 'https://nas.innerviewit.com/webapi/entry.cgi?api=SYNO.API.Auth',
    userList: 'https://nas.innerviewit.com/webapi/entry.cgi',
    chat:`https://nas.innerviewit.com/webapi/entry.cgi?api=SYNO.Chat.External&method=chatbot&version=2&token=%22${token}%22`,
    chatUserList: `https://nas.innerviewit.com/webapi/entry.cgi?api=SYNO.Chat.External&method=user_list&version=2`
};

const requestBody = {
    login: {
        version: 7,
        method: 'login',
        account: "",
        passwd: "",
        session: "",
        format: "cookie",
        api: "SYNO.API.Auth",
        otp_code: ""
    },
    logout: {
        'version': 7,
        'method': 'logout'
    },
    userList: {
        'version': 1,
        'method': 'list',
        'type': 'local',
        'offset': 0,
        'limit': -1,
        'additional': '["email","description","expired"]',
        'api': 'SYNO.Core.User'
    },
    sendMessage: {
        text: "",
        user_ids:[]
    }
};

module.exports = {
    urls,
    requestBody,
    token
};

