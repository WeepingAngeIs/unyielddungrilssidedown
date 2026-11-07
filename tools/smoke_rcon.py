"""Local disposable test server only; reads its private password without printing it."""
import socket,struct,sys
from pathlib import Path

class Rcon:
    def __init__(self):
        props=dict(line.split('=',1) for line in (Path(__file__).resolve().parents[1]/'run/server.properties').read_text().splitlines() if '=' in line)
        assert props.get('server-ip')=='127.0.0.1','Smoke script is restricted to loopback'
        self.sock=socket.create_connection(('127.0.0.1',int(props['rcon.port'])),timeout=30)
        self.counter=0
        self.send(3,props['rcon.password'])
        ident,kind,text=self.receive()
        if ident==-1:raise RuntimeError('Local RCON authentication failed')
    def exact(self,n):
        buf=b''
        while len(buf)<n:
            piece=self.sock.recv(n-len(buf))
            if not piece:raise EOFError('RCON closed')
            buf+=piece
        return buf
    def send(self,kind,text):
        self.counter+=1
        body=struct.pack('<ii',self.counter,kind)+text.encode()+b'\0\0'
        self.sock.sendall(struct.pack('<i',len(body))+body)
    def receive(self):
        size=struct.unpack('<i',self.exact(4))[0]
        if size<10 or size>1_000_000:raise RuntimeError('Bad RCON packet')
        raw=self.exact(size)
        ident,kind=struct.unpack('<ii',raw[:8])
        return ident,kind,raw[8:-2].decode(errors='replace')
    def command(self,text):
        self.send(2,text)
        return self.receive()[2]

if __name__=='__main__':
    r=Rcon()
    for cmd in sys.argv[1:]:print(cmd+'\n'+r.command(cmd))
