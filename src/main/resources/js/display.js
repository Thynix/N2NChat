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
      $.post('/n2n-chat/display/', { 'room' : room, 'message' : message, 'formPassword' : formPassword }, function() {
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
    $.post('/n2n-chat/display/', { 'room' : room, 'invite' : selectedPeer, 'formPassword' : formPassword }, function() {
      //Someone was invited or uninvited, so reflect that immediately in the participants pane.
      refreshParticipantsList();
    });
  });

  function refreshParticipantsList() {
    $.get('/n2n-chat/display/', { 'room' : room, 'participantsList' : 'only' }, function(data, status, jqXHR) {
      //Only update if there was a change.
      if (jqXHR.status == 200) {
        participantsList.html(data);
        //There was a change; perhaps someone accepted a pending invite and the drop-down changed.
        refreshInviteDropDown();
      }
    });
  }

  function refreshInviteDropDown() {
    $.get('/n2n-chat/display/', { 'room' : room, 'inviteDropDown' : 'only' }, function(data, status, jqXHR) {
      if (jqXHR.status == 200) {
        inviteContainer.html(data);
        //The invite drop-down changed, so the participants list must have changed.
        refreshParticipantsList();
      }
    });
  }

  function refreshMessagePane() {
    //If at the bottom, load new content, then scroll down to the new bottom.
    if (atBottom()) {
      $.get('/n2n-chat/display/', { 'room' : room, 'messagesPane' : 'only' }, function(data, status, jqXHR) {
        if (jqXHR.status == 200) {
          msgPane.html(data);
          msgPane.animate({scrollTop: msgPane[0].scrollHeight});
        }
      });
    } else {
       $.get('/n2n-chat/display/', { 'room' : room, 'messagesPane' : 'only' }, function(data, status, jqXHR) {
         if (jqXHR.status == 200) {
           msgPane.html(data);
         }
      });
    }
  }

  function atBottom() {
      //Total height of scrollable element - visible height - height scrollable area hidden above.
      //If this is less than 0,  it's scrolled down to the bottom line.
      return msgPane[0].scrollHeight - msgPane.height() - msgPane.scrollTop() < 0;
  }

  function refreshPanes() {
    refreshMessagePane();
    refreshParticipantsList();
    refreshInviteDropDown();
    setTimeout(refreshPanes, 1000);
  }

  refreshPanes();
});