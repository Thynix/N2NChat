$(document).ready(function() {
    //Needed for sending requests.
    var room = $('[name="room"]').val();
    var formPassword = $('[name="formPassword"]').val();

    //Put the elements in variables to avoid repetitive lookup calls.
    var element = $('[name="message"]');
    var msgPane = $('#messages-pane');
    var participantsList = $('#participants-list');
    var inviteContainer = $('#invite-container');
    var inviteSelect = $('[name="invite"]');

    //Enter in the messages text field.
    element.keydown(function(event) {
        if (event.which == 13) {
            //As this is being POSTed manually, suppress the form submitting again, which would submit twice.
            event.preventDefault();
            //Maintain focus on the text field.
            element.focus();
            var message = element.val();
            $.post('/n2n-chat/display/', {
                'room': room,
                'message': message,
                'formPassword': formPassword
            }, function() {
                element.val("");
                refreshMessagePane();
            });
        }
    });

    //Clicking on the send invite button.
    $('[name="send-invite"]').click(function(event) {
        //Suppress double-submit.
        event.preventDefault();
        var selectedPeer = inviteSelect.val();
        $.post('/n2n-chat/display/', {
            'room': room,
            'invite': selectedPeer,
            'formPassword': formPassword
        }, function() {
            var numberOfPeers = $('[name="invite"] option').size();
            //If going to go off the end, wrap to the beginning.
            if (inviteSelect[0].selectedIndex + 1 == numberOfPeers) {
                inviteSelect[0].selectedIndex = 0;
            } else {
                inviteSelect[0].selectedIndex++;
            }
            //Someone was invited or uninvited, so reflect that immediately in the participants pane.
            refreshParticipantsList();
        });
    });

    //The AJAX requests all return 200 only if there was a change. If cache is not false, some browsers (such as Firefox)
    //will treat the cache hit as a 200, which breaks things as it will load a stale, cached copy as an update.


    function refreshParticipantsList() {
        $.ajax({
            url: '/n2n-chat/display/',
            cache: false,
            data: {
                'room': room,
                'participantsList': 'only'
            },
            success: participantsListHandler,
            dataType: 'html'
        });
    }

    function refreshInviteDropDown() {
        $.ajax({
            url: '/n2n-chat/display/',
            cache: false,
            data: {
                'room': room,
                'inviteDropDown': 'only'
            },
            success: inviteDropDownHandler,
            dataType: 'html'
        });
    }

    function refreshMessagePane() {
        $.ajax({
            url: '/n2n-chat/display/',
            cache: false,
            data: {
                'room': room,
                'messagesPane': 'only'
            },
            success: messagePaneHandler,
            dataType: 'html'
        });
    }

    function participantsListHandler(data, status, jqXHR) {
        if (jqXHR.status == 200) {
            participantsList.html(data);
        }
    }

    function inviteDropDownHandler(data, status, jqXHR) {
        if (jqXHR.status == 200) {
            inviteContainer.html(data);
        }
    }

    //Scroll to new bottom if at the bottom before loading new data.


    function messagePaneHandler(data, status, jqXHR) {
        if (jqXHR.status == 200) {
            var scroll = atBottom();
            msgPane.html(data);
            if (scroll) {
                scrollToBottom(msgPane, true);
            }
        }
    }

    function atBottom() {
        //Total height of scrollable element - visible height - height scrollable area hidden above.
        //If this is less than 0,  it's scrolled down to the bottom line.
        return msgPane[0].scrollHeight - msgPane.height() - msgPane.scrollTop() < 0;
    }

    function scrollToBottom(element, animate) {
        if (animate) {
            element.animate({
                scrollTop: element[0].scrollHeight
            });
        } else {
            element.prop({
                scrollTop: element[0].scrollHeight
            });
        }
    }

    function refreshPanes() {
        refreshMessagePane();
        refreshParticipantsList();
        refreshInviteDropDown();
        setTimeout(refreshPanes, 1000);
    }

    //Scroll to bottom of messages pane so that it starts out at the latest messages if there are already messages
    //in this room.
    scrollToBottom(msgPane, false);

    setTimeout(refreshPanes, 1000);
});