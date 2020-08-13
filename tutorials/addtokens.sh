while read p; do
  ${BIN_DIR}/AuthorityMock update ${principalId} $p ${SAFE_SERVER}
done <cmds
