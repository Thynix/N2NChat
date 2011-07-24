function refreshInvitationTable() {
    $.ajax({
        url: '/n2n-chat/main-page/',
        cache: false,
        data: {
            'invitationTable': 'only'
        },
        success: function(data, status, jqXHR) {
            //Only update if there was a change.
            if (jqXHR.status == 200) {
                $('#invitationContainer').html(data);
            }
        },
        dataType: 'html'
    });
    setTimeout(refreshInvitationTable, 1000);
}

$(document).ready(function() {
    refreshInvitationTable();
});