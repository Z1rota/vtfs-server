package dev.vtfs.server.controller;

import dev.vtfs.server.exception.FsException;
import dev.vtfs.server.protocol.FsBinaryResponse;
import dev.vtfs.server.service.FsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.function.Supplier;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FsController {
    private final FsService fsService;

    @Value("${vtfs.token}")
    private String expectedToken;

    @GetMapping(value = "/stat",produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public byte[] stat(@RequestParam String token, @RequestParam Long inode) {
        return execute(token, () -> FsBinaryResponse.inode(fsService.stat(inode)));
    }

    @GetMapping(value = "/lookup",produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public byte[] lookup(@RequestParam String token, @RequestParam Long parent, @RequestParam String name) {

        return execute(token, () -> FsBinaryResponse.inode(fsService.lookup(parent,name)));
    }

    @GetMapping(value = "/list",produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public byte[] list(@RequestParam String token, @RequestParam Long parent) {
        return execute(token, () -> FsBinaryResponse.list(fsService.list(parent)));
    }

    @GetMapping(value="/create",produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public byte[] create(@RequestParam String token, @RequestParam Long parent, @RequestParam String name) {
        return execute(token, () -> FsBinaryResponse.inode(fsService.create(parent,name)));
    }

    @GetMapping(value="/mkdir",produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public byte[] mkdir(@RequestParam String token, @RequestParam Long parent, @RequestParam String name) {
        return execute(token, () -> FsBinaryResponse.inode(fsService.mkdir(parent,name)));
    }

    @GetMapping(value = "/unlink", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public byte[] unlink(@RequestParam String token, @RequestParam Long parent, @RequestParam String name) {
        return execute(token, () -> fsService.unlink(parent, name));
    }

    @GetMapping(value = "/rmdir",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public byte[] rmdir(@RequestParam String token, @RequestParam Long parent, @RequestParam String name) {
        return execute(token, () -> fsService.rmdir(parent, name));
    }

    @GetMapping(value="/read", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public byte[] read(@RequestParam String token, @RequestParam Long inode,@RequestParam long offset,
                       @RequestParam int length) {
        return execute(token, () -> FsBinaryResponse.read(fsService.read(inode,offset,length)));
    }

    @PostMapping(value="/write", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public byte[] write(@RequestParam String token, @RequestParam Long inode, @RequestParam Long offset,
                        @RequestParam(defaultValue = "false") boolean append,
                        @RequestBody(required = false) byte[] data) {

        return execute(token, () -> FsBinaryResponse.write(fsService.write(inode,offset,
                data == null ? new byte[0] : data, append)));

    }

    @PostMapping(value = "/truncate",produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public byte[] truncate(@RequestParam String token, @RequestParam Long inode, @RequestParam Long size) {
        return execute(token, () -> fsService.truncate(inode,size));
    }
    @GetMapping(value="/link", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public byte[] link(@RequestParam String token, @RequestParam Long inode, @RequestParam Long parent,
                       @RequestParam String name) {
        return execute(token, () -> FsBinaryResponse.inode(fsService.link(inode,parent,name)));
    }

    @GetMapping(value = "/rename",produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public byte[] rename(@RequestParam String token, @RequestParam Long oldParent, @RequestParam String oldName,
                         @RequestParam Long newParent, @RequestParam String newName) {
        return execute(token, () -> fsService.rename(oldParent,oldName,newParent,newName));
    }

    @GetMapping(value = "/root", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public byte[] root(@RequestParam String token) {
        return execute(token, () -> FsBinaryResponse.inode(fsService.root()));
    }


    private byte[] execute(String token, Supplier<byte[]> operation) {
        if (!expectedToken.equals(token)) {
            return FsBinaryResponse.status(13);
        }
        try {
            return operation.get();
        } catch (FsException e) {
            return FsBinaryResponse.status(e.getErrno());
        }
    }

    private byte[] execute(String token, Runnable operation) {
        return execute(token, () -> {
            operation.run();
            return FsBinaryResponse.status(0);
        });
    }
}
