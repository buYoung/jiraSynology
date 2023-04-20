const express = require('express');
const { sendMessage } = require("../../synology/chattingBot");
const router = express.Router();

/* GET home page. */
router.get('/', function(req, res, next) {
    res.send(global.userList);
});

router.post("/jira", async (req, res) => {
    if (!req.body || Object.keys(req.body).length === 0) {
        res.json('Bad request: JSON data is missing');
        return;
    }
    if (!req.body.message) {
        return res.json('Bad request: message is missing');
    }
    if (!req.body.user_ids && !req.body.user_emails) {
        return res.json('Bad request: user_ids or user_emails is missing');
    }
    const { message, user_ids, user_emails } = req.body;

    await sendMessage("jira", user_ids, user_emails, message);
    return res.json("TEst");
});

module.exports = router;
