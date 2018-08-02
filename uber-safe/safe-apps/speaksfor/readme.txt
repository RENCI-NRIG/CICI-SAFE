Testing speaksFor
=================

Issuer:
------
$ curl -H "Content-Type:application/json" -XPOST http://152.3.136.26:7777/postIdSet -d  '{ "principal": "key_p805", "otherValues": ["p805"]}'
{
  "message": "['1bKw1ggQFkINurtu_MNmcr5n5RG8BcdqqyTPx2D0u5w']"
}


Subject:
--------
$ curl -H "Content-Type:application/json" -XPOST http://152.3.136.26:777/postIdSet -d  '{ "principal": "key_p806", "otherValues": ["p806"]}'
{
  "message": "['83Ed2ump7BHHbbXLFmCDo3Ctf--08KQHp8qo2RlKcpY']"
}


$ curl -H "Content-Type:application/json" -XPOST http://152.3.136.26:7777/postASpeaksfor -d  '{ "principal": "key_p806", "otherValues": ["1bKw1ggQFkINurtu_MNmcr5n5RG8BcdqqyTPx2D0u5w"]}'
{
  "message": "['Tg8kQhLQ98fgimdHqAnj_eV3VuxCO6gJcXhbIxMQLrI']"
}

$ curl -H "Content-Type:application/json" -XPOST http://152.3.136.26:7777/validateSpeaksFor -d  '{ "principal": "key_p808", "otherValues": ["1bKw1ggQFkINurtu_MNmcr5n5RG8BcdqqyTPx2D0u5w", "83Ed2ump7BHHbbXLFmCDo3Ctf--08KQHp8qo2RlKcpY", "Tg8kQhLQ98fgimdHqAnj_eV3VuxCO6gJcXhbIxMQLrI"]}'
{
  "message": "{ 'YvF9XVfcOyACLKBBCHFVT5ravBgok9t2jD1QNLR1mK8':validSpeaksFor('1bKw1ggQFkINurtu_MNmcr5n5RG8BcdqqyTPx2D0u5w','83Ed2ump7BHHbbXLFmCDo3Ctf--08KQHp8qo2RlKcpY') }"
}

$ curl -H "Content-Type:application/json" -XPOST http://152.3.136.26:7777/postAccessPriv -d  '{ "principal": "key_p805", "otherValues": ["83Ed2ump7BHHbbXLFmCDo3Ctf--08KQHp8qo2RlKcpY", "Tg8kQhLQ98fgimdHqAnj_eV3VuxCO6gJcXhbIxMQLrI", "group0",  "obj0"]}'
{
  "message": "['_IE0ejLROpKBw1ep9W2i_l4G598AN7pFg61kYWZkbUc']"
}

$ curl -H "Content-Type:application/json" -XPOST http://152.3.136.26:7777/checkPriv -d  '{ "principal": "key_p808", "bearerRef": "_IE0ejLROpKBw1ep9W2i_l4G598AN7pFg61kYWZkbUc", "otherValues": ["83Ed2ump7BHHbbXLFmCDo3Ctf--08KQHp8qo2RlKcpY", "group0",  "obj0"]}'

$ curl -H "Content-Type:application/json" -XPOST http://152.3.136.26:7777/checkPriv -d  '{ "principal": "key_p808", "bearerRef": "_IE0ejLROpKBw1ep9W2i_l4G598AN7pFg61kYWZkbUc", "otherValues": ["83Ed2ump7BHHbbXLFmCDo3Ctf--08KQHp8qo2RlKcpY", "group0",  "obj0"]}'
{
  "message": "{ 'YvF9XVfcOyACLKBBCHFVT5ravBgok9t2jD1QNLR1mK8':approvedPriv('83Ed2ump7BHHbbXLFmCDo3Ctf--08KQHp8qo2RlKcpY',group0,obj0) }"
}
