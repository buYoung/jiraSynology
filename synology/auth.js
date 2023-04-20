const axios = require("axios");
const synoConst = require("./constants.js");
const {userList} = require("./userList.js");

async function login() {
    const loginBody = {...synoConst.requestBody.login};
    loginBody.account = 'notification';
    loginBody.passwd = '$INNER20';
    loginBody.session = 'webui';
    const response =
              await axios.get(synoConst.urls.login, {
                  params: loginBody,
                  headers: {
                      'Content-Type': 'application/x-www-form-urlencoded',
                  }
              });

    const responseData = response.data;
    const responseHeaders = response.headers;

    const isLoginSuccess = responseData.success;
    const loginData = responseData.data;
    if (!isLoginSuccess) {
        console.log("Login failed");
        return false;
    }

    const synotoken = loginData.synotoken;
    const setCookied = responseHeaders['set-cookie'];

    await userList(setCookied, synotoken);
    await logout(setCookied);
}
async function logout(cookie) {
    const logoutBody = {...synoConst.requestBody.logout};
    const response =
              await axios.post(synoConst.urls.logout, logoutBody,{
                  headers: {
                      'Content-Type': 'application/x-www-form-urlencoded',
                      'cookie': cookie
                  }
              });

    const responseData = response.data;

    const isLogoutSuccess = responseData.success;
    if (!isLogoutSuccess) {
        console.log("Logout failed");
        return false;
    }

    return true;
}


module.exports = {
    login,
    logout
}
