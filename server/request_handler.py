

# 每个用户的Sheet数量限制
max_sheet_count_in_user = 24
# 每个Sheet下的Note数量限制
max_note_count_in_sheet = 64
# 只保留最近N个版本内的删除记录
keep_deleted_note_ver = 256

# myDB
# 保存所有的数据
# 三级层次：用户 - Sheet - Note
# {
#   user: {
#       create_time: time,
#       pin: hash(password),
#       box: {
#           sheet: {
#               ver: ver,
#               note: {
#                   id: {
#                       ver: ver,
#                       time: time,
#                       text: text
#                   }
#               }
#               deleted:{
#                   id: ver
#               }
#           }
#       }
#   }
# }
#
# transaction 包括：
#   register, 注册新用户
#   logon, 登陆
#   unregister, 注销用户
#   modify_pin, 修改密码
#   create_sheet, 新建Sheet
#   rename_sheet, 重命名Sheet
#   delete_sheet, 删除Sheet
#   put_note, 新建/更新Note
#   get_new_notes, 下载比指定版本新的所有Note
#   delete_note, 删除Note


def request_handler(request, user_db):
    try:
        transaction = request['transaction']

        if transaction == 'modify_pin':
            respond = fun_modify_pin(request, user_db)
        elif transaction == 'create_sheet':
            respond = fun_create_sheet(request, user_db)
        elif transaction == 'rename_sheet':
            respond = fun_rename_sheet(request, user_db)
        elif transaction == 'delete_sheet':
            respond = fun_delete_sheet(request, user_db)
        elif transaction == 'put_note':
            respond = fun_put_note(request, user_db)
        elif transaction == 'get_new_notes':
            respond = fun_get_new_notes(request, user_db)
        elif transaction == 'delete_note':
            respond = fun_delete_note(request, user_db)
        else:
            respond = {'result': 'failed', 'reason': 'bad request'}

    except KeyError as e:
        respond = {'result': 'failed', 'reason': 'KeyError with ' + str(e)}

    # 执行成功时保持连接
    if respond['result'] != 'success':
        return respond, True
    else:
        return respond, False


def fun_modify_pin(request, user_db):
    new_pin = request['new_pin']
    if len(new_pin) < 6:
        return {'result': 'failed', 'reason': 'pin too short'}
    user_db['pin'] = new_pin
    return {'result': 'success'}


def fun_create_sheet(request, user_db):
    sheet = request['sheet']
    if len(sheet) == 0:
        return {'result': 'failed', 'reason': 'bad request'}
    # 数量限制
    if len(user_db['box']) >= max_sheet_count_in_user:
        return {'result': 'failed', 'reason': 'too many sheets'}
    # sheet不能是DB中已存在的
    if sheet in user_db['box']:
        return {'result': 'failed', 'reason': 'sheet already existed'}
    user_db['box'][sheet] = {
        'ver': 0,
        'note': {},
        'deleted': {}
    }
    return {'result': 'success'}


def fun_rename_sheet(request, user_db):
    sheet = request['sheet']
    new_sheet = request['new_sheet']
    if len(new_sheet) == 0:
        return {'result': 'failed', 'reason': 'bad request'}
    # sheet不能是DB中已存在的
    if new_sheet in user_db['box']:
        return {'result': 'failed', 'reason': 'sheet already existed'}
    user_db['box'][new_sheet] = user_db['box'][sheet]
    del user_db['box'][sheet]
    return {'result': 'success'}


def fun_delete_sheet(request, user_db):
    sheet = request['sheet']
    del user_db['box'][sheet]
    return {'result': 'success'}


def fun_put_note(request, user_db):
    sheet = request['sheet']
    note_id = request['note_id']
    modify_time = request['time']
    sheet_dict = user_db['box'][sheet]
    if len(note_id) == 0:
        return {'result': 'failed', 'reason': 'bad request'}
    # 数量限制
    if len(sheet_dict['note']) >= max_note_count_in_sheet:
        return {'result': 'failed', 'reason': 'too many notes'}
    # 更新版本
    ver = sheet_dict['ver']
    ver += 1
    sheet_dict['ver'] = ver
    # 更新note
    sheet_dict['note'][note_id] = {
        'ver': ver,
        'time': modify_time,
        'text': request['text']
    }
    # 检查deleted列表中是否有同名项
    if note_id in sheet_dict['deleted']:
        del sheet_dict['deleted'][note_id]
    return {'result': 'success'}


def fun_get_new_notes(request, user_db):
    sheet = request['sheet']
    client_db_version = request['client_db_version']
    sheet_dict = user_db['box'][sheet]
    ver = sheet_dict['ver']
    note_dict = sheet_dict['note']
    deleted_dict = sheet_dict['deleted']
    respond = {
        'result': 'success',
        'ver': ver,
        'new_notes': {},
        'deleted': []
    }
    respond_new_notes = respond['new_notes']
    respond_deleted = respond['deleted']

    # 如果客户端版本和服务端一致，不需要查询
    if client_db_version < ver:
        for note_id in note_dict:
            if note_dict[note_id]['ver'] > client_db_version:
                respond_new_notes[note_id] = note_dict[note_id]
        # 如果客户端数据版本是0，不需要查询删除记录
        if client_db_version > 0:
            for note_id in deleted_dict:
                delete_ver = deleted_dict[note_id]
                if delete_ver > client_db_version:
                    respond_deleted.append(note_id)
                # 删除太旧的记录
                if ver - delete_ver > keep_deleted_note_ver:
                    del deleted_dict[note_id]
    return respond


def fun_delete_note(request, user_db):
    sheet = request['sheet']
    note_id = request['note_id']
    sheet_dict = user_db['box'][sheet]
    # 删除note
    del sheet_dict['note'][note_id]
    # 更新版本
    ver = sheet_dict['ver']
    ver += 1
    sheet_dict['ver'] = ver
    # 记录到‘deleted'下
    sheet_dict['deleted'][note_id] = ver
    return {'result': 'success'}
