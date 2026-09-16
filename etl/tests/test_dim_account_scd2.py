from etl.dims import load_dim_account


def _account_row(account_id="ACC-10001", holder_name="Arun Kumar", status="ACTIVE", source_id=1):
    return {
        "account_id": account_id,
        "holder_name": holder_name,
        "status": status,
        "source_id": source_id,
    }


def test_new_account_is_inserted_as_current(con):
    load_dim_account(con, [_account_row()])

    row = con.execute(
        "SELECT status, is_current, end_date FROM dim_account WHERE account_id = 'ACC-10001'"
    ).fetchone()
    assert row == ("ACTIVE", True, None)


def test_status_change_closes_prior_version_and_inserts_new_current_one(con):
    load_dim_account(con, [_account_row(status="ACTIVE")])
    load_dim_account(con, [_account_row(status="SUSPENDED")])

    rows = con.execute(
        "SELECT status, is_current, end_date FROM dim_account "
        "WHERE account_id = 'ACC-10001' ORDER BY account_key"
    ).fetchall()

    assert len(rows) == 2
    assert rows[0][0] == "ACTIVE"
    assert rows[0][1] is False
    assert rows[0][2] is not None  # end_date set on the closed version
    assert rows[1][0] == "SUSPENDED"
    assert rows[1][1] is True
    assert rows[1][2] is None


def test_status_change_assigns_a_new_surrogate_key(con):
    load_dim_account(con, [_account_row(status="ACTIVE")])
    first_key = con.execute(
        "SELECT account_key FROM dim_account WHERE is_current = TRUE"
    ).fetchone()[0]

    load_dim_account(con, [_account_row(status="SUSPENDED")])
    second_key = con.execute(
        "SELECT account_key FROM dim_account WHERE is_current = TRUE"
    ).fetchone()[0]

    assert second_key != first_key


def test_unchanged_status_is_a_no_op(con):
    load_dim_account(con, [_account_row(status="ACTIVE")])
    load_dim_account(con, [_account_row(status="ACTIVE")])

    rows = con.execute(
        "SELECT COUNT(*) FROM dim_account WHERE account_id = 'ACC-10001'"
    ).fetchone()[0]
    assert rows == 1
