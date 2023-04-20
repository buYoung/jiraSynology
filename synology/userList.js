const synoConst = require("./constants.js");
const axios = require("axios");

async function userList(cookie, token) {
    const userListBody
              = {...synoConst.requestBody.userList};
    const response =
              await axios.post(synoConst.urls.userList, userListBody, {
                  headers: {
                      'Content-Type': 'application/x-www-form-urlencoded',
                      'cookie': cookie,
                      "x-syno-token": token
                  }
              });

    const responseData = response.data;
    const isUserListSuccess = responseData.success;
    if (!isUserListSuccess) {
        console.log("User list failed");
        return false;
    }

    const userList = [];
    const notAllowedUser = ["admin", "guest"];
    const userDatas = responseData.data.users;
    for (let userdata of userDatas) {
        if (notAllowedUser.includes(userdata.name.toLowerCase())) {
            continue;
        }
        if (userdata.expired === "now") {
            continue
        }
        console.log(userdata);
        userList.push(userdata);
    }

    global.userList = userList;
}

module.exports = {
    userList
}
